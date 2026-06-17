import { Suspense } from "react";
import { AlbumListView } from "@/components/AlbumListView";

export default function AlbumsPage() {
  return (
    <Suspense fallback={<div className="px-5 pt-4 text-sm text-inactive">불러오는 중…</div>}>
      <AlbumListView />
    </Suspense>
  );
}
