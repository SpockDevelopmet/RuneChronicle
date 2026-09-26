package com.runechronicle;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Resolves authentic OSRS Wiki page thumbnails for NPCs on demand.
 * Only the NPC page title is sent. No player/account/Chronicle data is transmitted.
 * Images are cached in-memory for the RuneLite session.
 */
final class NpcPortraitService
{
    private static final String API = "https://oldschool.runescape.wiki/api.php?action=query&format=json&prop=pageimages&pithumbsize=128&redirects=1&titles=";
    private static final String UA = "RuneChronicle/0.7.1 (RuneLite Plugin Hub)";
    private final OkHttpClient http;
    private final Gson gson;
    private final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    NpcPortraitService(OkHttpClient http, Gson gson)
    {
        this.http = http;
        this.gson = gson;
    }

    void apply(String npcName, JLabel target)
    {
        apply(npcName, target, 36);
    }

    void apply(String npcName, JLabel target, int size)
    {
        if (npcName == null || npcName.trim().isEmpty()) return;
        String key = npcName.trim().toLowerCase(Locale.ROOT) + "|" + size;
        ImageIcon hit = cache.get(key);
        if (hit != null)
        {
            target.setText("");
            target.setIcon(hit);
            target.setToolTipText(npcName);
            return;
        }
        if (!pending.add(key)) return;
        String encoded = URLEncoder.encode(npcName.trim(), StandardCharsets.UTF_8);
        Request req = new Request.Builder().url(API + encoded).header("User-Agent", UA).build();
        http.newCall(req).enqueue(new Callback()
        {
            @Override public void onFailure(Call call, java.io.IOException e) { pending.remove(key); }
            @Override public void onResponse(Call call, Response response)
            {
                try (Response r = response)
                {
                    if (!r.isSuccessful() || r.body() == null) return;
                    JsonObject root = gson.fromJson(r.body().charStream(), JsonObject.class);
                    JsonObject query = root == null ? null : root.getAsJsonObject("query");
                    JsonObject pages = query == null ? null : query.getAsJsonObject("pages");
                    if (pages == null) return;
                    String src = null;
                    for (Map.Entry<String, JsonElement> entry : pages.entrySet())
                    {
                        JsonObject page = entry.getValue().getAsJsonObject();
                        JsonObject thumb = page.getAsJsonObject("thumbnail");
                        if (thumb != null && thumb.has("source")) { src = thumb.get("source").getAsString(); break; }
                    }
                    if (src != null) fetchImage(key, npcName, src, target, size);
                }
                catch (Exception ignored) { }
                finally { pending.remove(key); }
            }
        });
    }

    private void fetchImage(String key, String npcName, String url, JLabel target, int size)
    {
        Request req = new Request.Builder().url(url).header("User-Agent", UA).build();
        http.newCall(req).enqueue(new Callback()
        {
            @Override public void onFailure(Call call, java.io.IOException e) { }
            @Override public void onResponse(Call call, Response response)
            {
                try (Response r = response)
                {
                    if (!r.isSuccessful() || r.body() == null) return;
                    BufferedImage raw = ImageIO.read(new ByteArrayInputStream(r.body().bytes()));
                    if (raw == null) return;
                    ImageIcon icon = new ImageIcon(fit(raw, size, size));
                    cache.put(key, icon);
                    SwingUtilities.invokeLater(() -> {
                        target.setText(""); target.setIcon(icon); target.setToolTipText(npcName);
                    });
                }
                catch (Exception ignored) { }
            }
        });
    }

    private static BufferedImage fit(BufferedImage src, int w, int h)
    {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double scale = Math.min((double) w / src.getWidth(), (double) h / src.getHeight());
        int dw = Math.max(1, (int)Math.round(src.getWidth() * scale));
        int dh = Math.max(1, (int)Math.round(src.getHeight() * scale));
        int x = (w - dw) / 2, y = (h - dh) / 2;
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, x, y, dw, dh, null);
        g.dispose();
        return out;
    }
}
