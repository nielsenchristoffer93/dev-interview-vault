import React, { Component, ErrorInfo, ReactNode } from 'react';

// ============================================================================
// 1. RESILIENT ERROR BOUNDARY (Skydd mot White Screen of Death)
// ============================================================================

export interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode | ((props: { error: Error; reset: () => void }) => ReactNode);
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  onReset?: () => void;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

/**
 * Resilient Error Boundary som fångar renderingsfel i underkomponenter,
 * förhindrar att hela React-rooten avmonteras, skickar telemetri (Sentry),
 * och ger användaren möjlighet att återställa komponenten (Retry).
 */
export class ResilientErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
    };
  }

  // Uppdaterar state så nästa rendering visar fallback-UI
  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return {
      hasError: true,
      error,
    };
  }

  // Fångar fel för biverkningar (telemetri/loggning till Sentry eller Datadog)
  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    if (this.props.onError) {
      this.props.onError(error, errorInfo);
    }
  }

  // Återställningsfunktion för "Försök igen" utan sidomladdning
  reset = (): void => {
    if (this.props.onReset) {
      this.props.onReset();
    }
    this.setState({
      hasError: false,
      error: null,
    });
  };

  render(): ReactNode {
    if (this.state.hasError && this.state.error) {
      if (typeof this.props.fallback === 'function') {
        return this.props.fallback({ error: this.state.error, reset: this.reset });
      }

      if (this.props.fallback) {
        return this.props.fallback;
      }

      // Default resilient fallback UI
      return (
        <div className="error-fallback-container" data-testid="error-fallback" role="alert">
          <h4>Något gick fel i denna vy</h4>
          <p className="error-message">{this.state.error.message}</p>
          <button type="button" onClick={this.reset} data-testid="retry-button">
            Försök igen
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

// ============================================================================
// 2. CLS OPTIMIZATION: Reserverade dimensioner och Skeleton
// ============================================================================

export interface StableAspectContainerProps {
  aspectRatio?: string; // T.ex. "16 / 9" eller "4 / 3"
  minHeight?: number | string;
  isLoading?: boolean;
  children?: ReactNode;
}

/**
 * StableAspectContainer reserverar fysisk layout-plats innan asynkron data
 * eller bilder anländer. Detta garanterar 0 layout shift (CLS = 0).
 */
export const StableAspectContainer: React.FC<StableAspectContainerProps> = ({
  aspectRatio = '16 / 9',
  minHeight = '200px',
  isLoading = false,
  children,
}) => {
  return (
    <div
      className="stable-aspect-container"
      data-testid="stable-aspect-container"
      style={{
        aspectRatio,
        minHeight,
        width: '100%',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {isLoading ? (
        <div
          className="skeleton-placeholder"
          data-testid="skeleton-placeholder"
          style={{
            width: '100%',
            height: '100%',
            backgroundColor: '#e2e8f0',
            animation: 'pulse 1.5s infinite',
          }}
          aria-busy="true"
        />
      ) : (
        children
      )}
    </div>
  );
};

// ============================================================================
// 3. INP OPTIMIZATION: Time-sliced chunked task (Non-blocking yield)
// ============================================================================

/**
 * Delar upp tunga beräkningar i mindre tids-chunks (time slicing)
 * och gör yield till Main Thread via micro-pauser för att hålla INP < 50ms.
 */
export async function processDataWithYielding(
  totalItems: number,
  chunkSize: number = 500,
  yieldDelayMs: number = 0
): Promise<number> {
  let result = 0;

  for (let i = 0; i < totalItems; i += chunkSize) {
    const end = Math.min(i + chunkSize, totalItems);
    for (let j = i; j < end; j++) {
      result += Math.sqrt(j) * Math.sin(j);
    }

    // Ge tillbaka kontrollen till webbläsarens event loop för att tillåta paint & input
    if (end < totalItems) {
      await new Promise((resolve) => setTimeout(resolve, yieldDelayMs));
    }
  }

  return result;
}
