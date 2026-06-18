"use client";

import { useHome } from "@/lib/queries";
import { useAlbums } from "@/lib/albums";
import { Hero } from "@/components/Hero";
import { ContentContainer } from "@/components/ContentContainer";
import { HRow, HItem } from "@/components/HRow";
import { ContentCard } from "@/components/ContentCard";
import { AlbumCard } from "@/components/AlbumCard";
import { PostCard } from "@/components/PostCard";
import { NearbyChurchesSection } from "@/components/NearbyChurchesSection";
import { EmptyState, ErrorState } from "@/components/States";

function HomeSkeleton() {
  return (
    <div>
      <div className="skeleton aspect-[16/10] w-full" />
      <div className="mt-7 space-y-5 px-5">
        <div className="skeleton h-6 w-32 rounded" />
        <div className="flex gap-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="w-[240px]">
              <div className="skeleton aspect-video rounded-card" />
              <div className="skeleton mt-2.5 h-4 w-4/5 rounded" />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

/** "CCM 음반" carousel on the home screen — gives album purchases equal billing with video/music. */
function CcmAlbumSection() {
  const { data, isLoading } = useAlbums({ pageNumber: 0, pageSize: 10 });
  const albums = data?.contents ?? [];

  if (isLoading) {
    return (
      <ContentContainer title="CCM 음반" moreHref="/albums">
        <HRow>
          {Array.from({ length: 4 }).map((_, i) => (
            <HItem key={i} width={150}>
              <div className="skeleton aspect-square rounded-card" />
              <div className="skeleton mt-2.5 h-4 w-4/5 rounded" />
            </HItem>
          ))}
        </HRow>
      </ContentContainer>
    );
  }
  if (albums.length === 0) return null;

  return (
    <ContentContainer title="CCM 음반" moreHref="/albums">
      <HRow>
        {albums.map((album) => (
          <HItem key={album.id} width={150}>
            <AlbumCard item={album} />
          </HItem>
        ))}
      </HRow>
    </ContentContainer>
  );
}

export default function HomePage() {
  const { data, isLoading, isError, error, refetch } = useHome();

  if (isLoading) return <HomeSkeleton />;
  if (isError)
    return (
      <div className="pt-6">
        <ErrorState message={(error as Error)?.message} onRetry={() => refetch()} />
      </div>
    );
  if (!data) return <div className="pt-6"><EmptyState /></div>;

  return (
    <div className="animate-fade-up pb-4">
      <Hero items={data.videos} />

      <ContentContainer title="최신 영상" moreHref="/video">
        {data.videos.length > 0 ? (
          <HRow>
            {data.videos.map((v) => (
              <HItem key={v.id} width={240}>
                <ContentCard item={v} />
              </HItem>
            ))}
          </HRow>
        ) : (
          <EmptyState message="등록된 영상이 없습니다." />
        )}
      </ContentContainer>

      <ContentContainer title="찬양 음악" moreHref="/music">
        {data.musics.length > 0 ? (
          <HRow>
            {data.musics.map((m) => (
              <HItem key={m.id} width={150}>
                <ContentCard item={m} />
              </HItem>
            ))}
          </HRow>
        ) : (
          <EmptyState message="등록된 음악이 없습니다." />
        )}
      </ContentContainer>

      <CcmAlbumSection />

      <NearbyChurchesSection />

      <ContentContainer title="새로운 소식" moreHref="/posts">
        {data.posts.length > 0 ? (
          <div className="space-y-2.5 px-5">
            {data.posts.slice(0, 4).map((post) => (
              <PostCard key={post.id} post={post} />
            ))}
          </div>
        ) : (
          <EmptyState message="등록된 소식이 없습니다." />
        )}
      </ContentContainer>
    </div>
  );
}
