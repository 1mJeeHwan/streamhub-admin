package org.streamhub.api.v1.chat.adapter;

/**
 * A rich-message card a {@link ChatProvider} can attach to a reply (G): the widget renders it as an
 * image/title/subtitle tile with a deep-link button. Used for product/content results so the user
 * can jump straight to the item instead of reading a plain text list.
 *
 * @param title    card heading (e.g. product name)
 * @param subtitle secondary line (e.g. "₩10,000 · 재고 5개"); may be null
 * @param imageUrl thumbnail URL; may be null (widget shows a placeholder)
 * @param href     in-app deep link (e.g. {@code /goods/12}); rendered through a safe-href guard
 * @param badge    short status chip (e.g. "품절"); may be null
 */
public record ChatCard(String title, String subtitle, String imageUrl, String href, String badge) {
}
