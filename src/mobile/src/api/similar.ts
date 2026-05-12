/**
 * Phase B — Naver-sourced "비슷한 실제 상품" cache reader.
 * Wraps `GET /api/v1/furniture/{id}/similar` on the Spring backend.
 *
 * Returns an empty array when the cache is empty (typical for fresh
 * dev environments without Naver credentials, or before the nightly
 * batch has run). Callers render the "준비 중" placeholder in that
 * case rather than treating it as an error.
 */

export type SimilarProduct = {
  source: string;            // 'NAVER'
  externalId: string;
  rankOrder: number;
  title: string;
  externalUrl: string;
  imageUrl: string;
  price: number | null;
  mallName: string | null;
  similarityScore: number;
  fetchedAt: string;
};

export type SimilarProductsResponse = {
  furnitureId: string;
  items: SimilarProduct[];
};

type GetSimilarArgs = {
  baseUrl: string;
  furnitureId: string;
};

export async function getFurnitureSimilar(opts: GetSimilarArgs): Promise<SimilarProductsResponse> {
  const { baseUrl, furnitureId } = opts;
  const url = `${baseUrl}/api/v1/furniture/${encodeURIComponent(furnitureId)}/similar`;
  const res = await fetch(url, { method: 'GET', headers: { Accept: 'application/json' } });
  if (!res.ok) {
    throw new Error(`getFurnitureSimilar failed: ${res.status}`);
  }
  return (await res.json()) as SimilarProductsResponse;
}
