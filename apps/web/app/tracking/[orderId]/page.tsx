import TrackingPageClient from "./TrackingPageClient";

export function generateStaticParams() {
  return [{ orderId: "app" }];
}

export default function TrackingPage({ params }: { params: Promise<{ orderId: string }> }) {
  return <TrackingPageClient params={params} />;
}
