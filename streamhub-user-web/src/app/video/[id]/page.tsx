"use client";

import { useEffect } from "react";
import { useContent } from "@/lib/queries";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { recordWatch } from "@/lib/me";
import { VideoPlayer } from "@/components/VideoPlayer";
import { ContentMeta } from "@/components/ContentMeta";
import { BackLink } from "@/components/BackLink";
import { EmptyState, ErrorState } from "@/components/States";

export default function VideoDetailPage({ params }: { params: { id: string } }) {
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
        <BackLink href="/video" label="영상 목록" />
      </div>

      {isLoading ? (
        <div className="mt-3">
          <div className="skeleton aspect-video w-full" />
          <div className="space-y-2 px-5 pt-4">
            <div className="skeleton h-6 w-3/5 rounded" />
            <div className="skeleton h-4 w-2/5 rounded" />
          </div>
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
        <article className="mt-3">
          <VideoPlayer src={data.mediaUrl} title={data.title} />
          <div className="px-5 pt-4">
            <h1 className="text-xl font-bold leading-snug">{data.title}</h1>
            {data.channelName && <p className="mt-1 text-sm text-inactive">{data.channelName}</p>}
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
