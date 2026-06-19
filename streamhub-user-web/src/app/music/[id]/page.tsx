"use client";

import { useEffect } from "react";
import { useContent } from "@/lib/queries";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { recordWatch } from "@/lib/me";
import { AudioPlayer } from "@/components/AudioPlayer";
import { ContentMeta } from "@/components/ContentMeta";
import { BackLink } from "@/components/BackLink";
import { EmptyState, ErrorState } from "@/components/States";

export default function MusicDetailPage({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  const { data, isLoading, isError, error, refetch } = useContent(id);
  const { token } = useAuth();

  // Best-effort watch record: once per (member, content) view; no-op when anonymous.
  useEffect(() => {
    if (data && token) recordWatch(id, token);
  }, [data, token, id]);

  return (
    <div className="animate-fade-up">
      <div className="px-5 pt-4">
        <BackLink href="/music" label="음악 목록" />
      </div>

      {isLoading ? (
        <div className="px-5 pt-4">
          <div className="skeleton mx-auto aspect-square w-full max-w-[280px] rounded-card" />
          <div className="skeleton mx-auto mt-5 h-6 w-2/5 rounded" />
        </div>
      ) : isError ? (
        <div className="pt-3">
          {(error as ApiError)?.status === 404 ? (
            <EmptyState message="콘텐츠를 찾을 수 없습니다." />
          ) : (
            <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />
          )}
        </div>
      ) : data ? (
        <article className="pt-3">
          <AudioPlayer
            src={data.mediaUrl}
            contentId={id}
            hlsAvailable={!!data.hlsPrefix}
            title={data.title}
            channelName={data.channelName}
            thumbnailUrl={data.thumbnailUrl}
          />
          <div className="px-5">
            <ContentMeta
              viewCount={data.viewCount}
              createdAt={data.createdAt}
              hashtags={data.hashtags}
              description={data.description}
            />
          </div>
        </article>
      ) : null}
    </div>
  );
}
