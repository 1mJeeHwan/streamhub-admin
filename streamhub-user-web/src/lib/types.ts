// Types mirror the backend public DTOs (org.streamhub.api.v1.*). Kept in sync by hand —
// the public surface is small (5 endpoints), so this is simpler and safer than codegen.

export type ContentType = "VIDEO" | "SOUND";

/** One row of a content list (hashtags is a comma-joined string from GROUP_CONCAT). */
export interface ContentListItem {
  id: number;
  title: string;
  type: ContentType;
  status: string;
  channelId: number;
  channelName: string | null;
  churchName: string | null;
  thumbnailKey: string | null;
  thumbnailUrl: string | null;
  viewCount: number;
  durationSec: number | null;
  hashtags: string | null;
  createdAt: string;
}

/** Full content detail (hashtags is a string array). */
export interface ContentDetail {
  id: number;
  title: string;
  description: string | null;
  type: ContentType;
  status: string;
  channelId: number;
  channelName: string | null;
  churchName: string | null;
  mediaUrl: string;
  /** S3 prefix of the public HLS stream when packaged; null → play mediaUrl directly. */
  hlsPrefix: string | null;
  thumbnailKey: string | null;
  thumbnailUrl: string | null;
  durationSec: number | null;
  viewCount: number;
  hashtags: string[];
  createdAt: string;
  updatedAt: string;
}

export interface PostListItem {
  id: number;
  title: string;
  excerpt: string | null;
  thumbnailKey: string | null;
  thumbnailUrl: string | null;
  createdAt: string;
}

export interface PostDetail {
  id: number;
  title: string;
  body: string;
  thumbnailKey: string | null;
  thumbnailUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface HomeBundle {
  videos: ContentListItem[];
  musics: ContentListItem[];
  posts: PostListItem[];
}

export type BannerTarget = "VIDEO" | "SOUND" | "ALL";

/** A content-tab promo banner managed in the admin (org.streamhub.api.v1.banner). */
export interface BannerItem {
  id: number;
  title: string;
  subtitle: string | null;
  targetType: BannerTarget | null;
  imageUrl: string | null;
  linkUrl: string | null;
  sortOrder: number;
}

/** Standard API envelope; "0000" means success. */
export interface ResultDTO<T> {
  resultCode: string;
  resultMessage: string;
  resultObject: T;
}

export interface InfinityList<T> {
  contents: T[];
  totalCount: number;
  totalPage: number;
}

/** Logged-in member profile (no password). */
export interface MemberInfo {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  churchName: string | null;
  createdAt: string;
}

export interface MemberAuthResponse {
  token: string;
  expiresIn: number;
  member: MemberInfo;
}
