import React, { useState } from 'react';

export interface ProductData {
  id: string;
  name: string;
  details?: {
    price: number;
    currency: string;
  };
}

/**
 * FLAW 1: Ingen Error Boundary.
 * Om `details` saknas kraschar denna komponent med TypeError.
 * I React avmonterar detta hela appen och orsakar White Screen of Death (WSoD).
 */
export const FlawedProductWidget: React.FC<{ product: ProductData }> = ({ product }) => {
  // Kraschar om product.details är undefined:
  return (
    <div className="product-widget" data-testid="product-widget">
      <h3>{product.name}</h3>
      <p>Pris: {product.details!.price} {product.details!.currency}</p>
    </div>
  );
};

/**
 * FLAW 2: Saknar dimensioner & aspect-ratio reservation.
 * När bilden eller bannern laddas asynkront expanderar containern från 0px till 250px höjd,
 * vilket trycker ner all efterföljande text och knappar (orsakar massiv Cumulative Layout Shift / CLS).
 */
export const FlawedBanner: React.FC<{ imageUrl?: string }> = ({ imageUrl }) => {
  // Inga fasta mått, ingen skeleton, ingen CSS aspect-ratio:
  // Renderar ingenting (0px) tills imageUrl anländer, sedan plötsligt 250px hopp!
  if (!imageUrl) {
    return null;
  }

  return (
    <div className="banner-wrapper">
      <img src={imageUrl} alt="Campaign Promo" />
    </div>
  );
};

/**
 * FLAW 3: Blockerande Long Task på Main Thread vid användarinteraktion.
 * Kör en tung synkron loop (>50ms) inuti klickhanteraren.
 * Webbläsaren kan inte rita nästa frame (paint) vilket resulterar i usel INP (>500ms).
 */
export const flawedProcessDataSynchronously = (itemsCount: number): number => {
  const start = performance.now();
  let total = 0;
  // Simulerar tung synkron CPU-loop som blockerar Main Thread
  for (let i = 0; i < itemsCount; i++) {
    total += Math.sqrt(i) * Math.sin(i);
  }
  return total;
};
