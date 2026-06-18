import type { Metadata } from "next";
import "./globals.css";
import { Providers } from "./providers";
import { AppBar } from "@/components/AppBar";
import { TabBar } from "@/components/TabBar";
import { MiniPreviewPlayer } from "@/components/preview/MiniPreviewPlayer";
import { ChatbotWidget } from "@/components/ChatbotWidget";
import { AnnouncementModal } from "@/components/AnnouncementModal";
import { fetchSiteConfig, hexToRgbChannels } from "@/lib/siteConfig";

export const metadata: Metadata = {
  title: "StreamHub — 함께 드리는 예배",
  description: "예배 영상, 찬양 음악, 그리고 소식을 한 곳에서. 로그인 없이 자유롭게 둘러보세요.",
};

export const viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // Admin-controlled UI settings (theme/accent/announcement). Server-fetched so the theme +
  // accent apply before paint with no flash; never throws (falls back to defaults).
  const config = await fetchSiteConfig();
  const accentChannels = hexToRgbChannels(config.accentColor);
  // No-flash theme: the user's own toggle (localStorage) wins; otherwise the admin default.
  const themeScript =
    "(function(){try{var t=localStorage.getItem('streamhub.theme');if(!t)t='" +
    config.defaultTheme +
    "';if(t==='light'||t==='dark'){document.documentElement.setAttribute('data-theme',t);}}catch(e){}})();";

  return (
    <html
      lang="ko"
      // Admin accent overrides the --primary token in both themes (inline > stylesheet).
      style={accentChannels ? ({ "--primary": accentChannels } as React.CSSProperties) : undefined}
    >
      <head>
        {/* No-flash theme: apply the stored choice (or admin default) before first paint. */}
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Noto+Sans+KR:wght@400;500;700&display=swap"
        />
      </head>
      <body className="font-sans">
        <Providers>
          <div className="app-frame">
            <AppBar />
            <main className="min-h-[60vh] pb-[88px]">{children}</main>
            <MiniPreviewPlayer />
            <TabBar />
            <ChatbotWidget />
            <AnnouncementModal
              enabled={config.announcement.enabled}
              text={config.announcement.text}
              link={config.announcement.link || undefined}
            />
          </div>
        </Providers>
      </body>
    </html>
  );
}
