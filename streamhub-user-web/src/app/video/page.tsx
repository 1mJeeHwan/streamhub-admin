import { Suspense } from "react";
import { MediaBrowse } from "@/components/MediaBrowse";

export default function VideoListPage() {
  return (
    <Suspense fallback={<div className="px-5 pt-4 text-sm text-inactive">불러오는 중…</div>}>
      <MediaBrowse type="VIDEO" title="영상" searchPlaceholder="영상 검색" showChannels />
    </Suspense>
  );
}
