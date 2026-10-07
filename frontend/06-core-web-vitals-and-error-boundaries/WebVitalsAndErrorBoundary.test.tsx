import React from 'react';
import { createRoot } from 'react-dom/client';
import { act } from 'react';
import { describe, it, expect, vi } from 'vitest';
import {
  FlawedProductWidget,
  FlawedBanner,
  flawedProcessDataSynchronously,
  ProductData,
} from './FlawedWebVitals';
import {
  ResilientErrorBoundary,
  StableAspectContainer,
  processDataWithYielding,
} from './ResilientErrorBoundary';

describe('06-core-web-vitals-and-error-boundaries: Error Boundaries & Performance Lab', () => {
  // ==========================================================================
  // SCENARIO 1: ERROR BOUNDARY & WHITE SCREEN OF DEATH
  // ==========================================================================
  describe('Scenario 1: Error Boundary & White Screen of Death', () => {
    it('The Flaw: Ohanterat fel i underkomponent kraschar utan skydd', () => {
      const brokenProduct: ProductData = {
        id: 'p1',
        name: 'Tröja',
        details: undefined, // Kommer krascha!
      };

      // Att anropa funktionen direkt utan boundary kastar TypeError
      expect(() => {
        FlawedProductWidget({ product: brokenProduct });
      }).toThrow();
    });

    it('The Fix: ResilientErrorBoundary fångar felet, visar fallback-UI och skickar telemetri', async () => {
      const container = document.createElement('div');
      document.body.appendChild(container);
      const root = createRoot(container);

      const onErrorMock = vi.fn();
      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      const ThrowingChild = () => {
        throw new Error('Simulation: Sentry error trigger');
      };

      await act(async () => {
        root.render(
          <ResilientErrorBoundary onError={onErrorMock}>
            <ThrowingChild />
          </ResilientErrorBoundary>
        );
      });

      // Bevis: Fallback UI renderas istället för vit skärm!
      const fallback = container.querySelector('[data-testid="error-fallback"]');
      expect(fallback).not.toBeNull();
      expect(fallback?.textContent).toContain('Något gick fel i denna vy');
      expect(fallback?.textContent).toContain('Simulation: Sentry error trigger');

      // Bevis: Telemetri anropades
      expect(onErrorMock).toHaveBeenCalledTimes(1);
      expect(onErrorMock.mock.calls[0][0].message).toBe('Simulation: Sentry error trigger');

      consoleErrorSpy.mockRestore();
      root.unmount();
      document.body.removeChild(container);
    });
  });

  // ==========================================================================
  // SCENARIO 2: CLS & ASPECT-RATIO RESERVATION
  // ==========================================================================
  describe('Scenario 2: Cumulative Layout Shift (CLS) Prevention', () => {
    it('The Flaw: FlawedBanner saknar mått före bildladdning och orsakar reflow', () => {
      // Innan bild finns renderas null (0px i DOM)
      const unrendered = FlawedBanner({ imageUrl: undefined });
      expect(unrendered).toBeNull();
    });

    it('The Fix: StableAspectContainer reserverar plats och visar skeleton före laddning', async () => {
      const container = document.createElement('div');
      document.body.appendChild(container);
      const root = createRoot(container);

      await act(async () => {
        root.render(
          <StableAspectContainer aspectRatio="16 / 9" minHeight="250px" isLoading={true}>
            <img src="https://example.com/banner.jpg" alt="Loaded banner" />
          </StableAspectContainer>
        );
      });

      // Bevis: Containern har reserverat mått och skeleton visas
      const aspectBox = container.querySelector('[data-testid="stable-aspect-container"]') as HTMLElement;
      expect(aspectBox).not.toBeNull();
      expect(aspectBox.style.aspectRatio).toBe('16 / 9');
      expect(aspectBox.style.minHeight).toBe('250px');

      const skeleton = container.querySelector('[data-testid="skeleton-placeholder"]');
      expect(skeleton).not.toBeNull();

      root.unmount();
      document.body.removeChild(container);
    });
  });

  // ==========================================================================
  // SCENARIO 3: INP & YIELDING / TIME-SLICING
  // ==========================================================================
  describe('Scenario 3: Interaction to Next Paint (INP) & Time Slicing', () => {
    it('The Flaw vs The Fix: Chunked processing ger samma matematiska resultat utan att monopolisera tråden', async () => {
      const itemCount = 2000;

      // Synkron beräkning (blockerar tråden)
      const syncResult = flawedProcessDataSynchronously(itemCount);

      // Asynkron chunkad beräkning (gör yields och låter UI andas för god INP)
      const asyncResult = await processDataWithYielding(itemCount, 500, 1);

      // Verifiera att båda producerar identiskt resultat
      expect(asyncResult).toBe(syncResult);
      expect(typeof asyncResult).toBe('number');
    });
  });
});
