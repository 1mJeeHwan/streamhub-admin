import { ChurchFinderView } from "@/components/ChurchFinderView";

export const metadata = {
  title: "교회찾기 — StreamHub",
  description: "현위치 기준으로 가까운 교회를 거리순으로 찾아보세요.",
};

export default function ChurchesPage() {
  return (
    <div className="animate-fade-up">
      <ChurchFinderView />
    </div>
  );
}
