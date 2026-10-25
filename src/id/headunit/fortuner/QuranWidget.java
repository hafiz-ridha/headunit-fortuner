package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Widget Al-Qur'an di beranda: satu ayat beserta transliterasi dan terjemahan Kemenag, tombol
 * ayat sebelum/berikutnya, putar/jeda murattal, buka mushaf penuh, dan pilihan tampilan.
 */
final class QuranWidget extends LinearLayout implements QuranPlayer.Listener {
    private final QuranPlayer player;
    private final QuranRepository repo;
    private final TextView title;
    private final TextView subtitle;
    private final ImageView playBtn;
    private final ImageView badge;
    private final TextView arabic;
    private final TextView latin;
    private final TextView translation;
    private final TextView status;
    private final ScrollView scroll;
    private final LinearLayout body;
    private boolean overflow;

    @SuppressLint("ViewConstructor")
    QuranWidget(Context c, Runnable openFull) {
        super(c);
        player = QuranPlayer.get(c);
        repo = QuranRepository.get(c);
        setOrientation(VERTICAL);
        setBackground(Icons.quranCardBackground(Ui.px(22), false));
        setPadding(Ui.px(22), Ui.px(16), Ui.px(18), Ui.px(12));

        LinearLayout header = Ui.row(c);
        header.addView(Ui.iconView(c, Icons.medallion(), 50));
        header.addView(Ui.space(c, 16, 1));
        LinearLayout titles = Ui.column(c);
        title = Ui.text(c, "", 26, Ui.TEXT, Ui.MEDIUM);
        title.setSingleLine(true);
        subtitle = Ui.text(c, "", 16, Ui.TEXT2, Ui.REGULAR);
        titles.addView(title);
        titles.addView(subtitle);
        ((LayoutParams) subtitle.getLayoutParams()).topMargin = Ui.px(6);
        header.addView(titles, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        playBtn = Ui.roundButton(c, R.drawable.ic_play, 50, 0xFF1B2E48, Ui.TEXT);
        playBtn.setContentDescription("Putar murattal");
        playBtn.setOnClickListener(v -> player.toggle());
        ImageView book = Ui.roundButton(c, R.drawable.ic_book, 50, 0xFF1B2E48, Ui.TEXT);
        book.setContentDescription("Buka Al-Qur'an penuh");
        book.setOnClickListener(v -> openFull.run());
        ImageView gear = Ui.roundButton(c, R.drawable.ic_settings, 50, 0xFF1B2E48, Ui.TEXT);
        gear.setContentDescription("Pilihan tampilan Al-Qur'an");
        gear.setOnClickListener(this::showOptions);
        header.addView(playBtn);
        header.addView(Ui.space(c, 12, 1));
        header.addView(book);
        header.addView(Ui.space(c, 12, 1));
        header.addView(gear);
        addView(header);

        LinearLayout middle = Ui.row(c);
        ImageView prev = chevron(c, R.drawable.ic_chevron_left, "Ayat sebelumnya");
        prev.setOnClickListener(v -> player.previous());
        ImageView next = chevron(c, R.drawable.ic_chevron_right, "Ayat berikutnya");
        next.setOnClickListener(v -> player.next());

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        body = Ui.column(c);
        body.setGravity(Gravity.CENTER);
        body.setOnClickListener(v -> openFull.run());
        LinearLayout arabicRow = Ui.row(c);
        arabicRow.setGravity(Gravity.CENTER);
        badge = new ImageView(c);
        arabic = Ui.text(c, "", 32, Ui.TEXT, Ui.arabic(c));
        arabic.setIncludeFontPadding(true);
        arabic.setGravity(Gravity.CENTER);
        arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        arabic.setLineSpacing(Ui.px(6), 1f);
        arabicRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        arabicRow.addView(badge, new LayoutParams(Ui.px(40), Ui.px(40)));
        arabicRow.addView(Ui.space(c, 14, 1));
        arabicRow.addView(arabic, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        body.addView(arabicRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        latin = Ui.text(c, "", 18, 0xFFE8D7B0, Ui.REGULAR);
        latin.setGravity(Gravity.CENTER);
        latin.setTextDirection(View.TEXT_DIRECTION_LTR);
        translation = Ui.text(c, "", 18, Ui.TEXT, Ui.REGULAR);
        translation.setGravity(Gravity.CENTER);
        translation.setTextDirection(View.TEXT_DIRECTION_LTR);
        translation.setLineSpacing(Ui.px(4), 1f);
        body.addView(latin, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        ((LayoutParams) latin.getLayoutParams()).topMargin = Ui.px(10);
        body.addView(translation, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        ((LayoutParams) translation.getLayoutParams()).topMargin = Ui.px(6);
        scroll.addView(body, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        middle.addView(prev);
        middle.addView(scroll, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        middle.addView(next);
        addView(middle, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        ((LayoutParams) middle.getLayoutParams()).topMargin = Ui.px(6);

        status = Ui.text(c, "", 13, Ui.WARN, Ui.REGULAR);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(GONE);
        addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot || r - l != or - ol) v.post(this::fitText);
        });
        render();
    }

    private ImageView chevron(Context c, int res, String desc) {
        ImageView v = Ui.iconView(c, res, Ui.TEXT2, 44);
        int pad = Ui.px(6);
        v.setPadding(pad, pad, pad, pad);
        v.setLayoutParams(new LayoutParams(Ui.px(48), Ui.px(64)));
        v.setContentDescription(desc);
        Ui.pressable(v);
        return v;
    }

    private void showOptions(View anchor) {
        PopupMenu m = new PopupMenu(getContext(), anchor);
        m.getMenu().add(0, 1, 0, "Transliterasi Latin").setCheckable(true)
                .setChecked(Prefs.flag(getContext(), Prefs.SHOW_LATIN, true));
        m.getMenu().add(0, 2, 1, "Terjemahan Indonesia (Kemenag)").setCheckable(true)
                .setChecked(Prefs.flag(getContext(), Prefs.SHOW_TRANSLATION, true));
        m.setOnMenuItemClickListener(item -> {
            String key = item.getItemId() == 1 ? Prefs.SHOW_LATIN : Prefs.SHOW_TRANSLATION;
            Prefs.set(getContext(), key, !item.isChecked());
            render();
            return true;
        });
        m.show();
    }

    @Override
    public void onQuranChanged() { render(); }

    void render() {
        QuranRepository.SurahInfo info = repo.info(player.surah());
        QuranRepository.Ayah a = repo.ayah(player.surah(), player.ayah());
        if (info == null || a == null) {
            title.setText("Al-Qur'an");
            subtitle.setText("Data Al-Qur'an tidak terbaca");
            return;
        }
        title.setText(info.latin);
        subtitle.setText("Surah ke-" + info.number + "  ·  " + info.ayahCount + " Ayat  ·  Ayat " + a.number);
        Drawable d = Icons.ayahBadge(a.number);
        badge.setImageDrawable(d);
        arabic.setText(a.arabic);
        latin.setText(a.latin);
        translation.setText(a.translation);
        latin.setVisibility(Prefs.flag(getContext(), Prefs.SHOW_LATIN, true) ? VISIBLE : GONE);
        translation.setVisibility(Prefs.flag(getContext(), Prefs.SHOW_TRANSLATION, true) ? VISIBLE : GONE);
        playBtn.setImageDrawable(Ui.icon(getContext(), player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play,
                player.isPlaying() ? Ui.GOLD : Ui.TEXT));
        playBtn.setContentDescription(player.isPlaying() ? "Jeda murattal" : "Putar murattal");
        scroll.scrollTo(0, 0);
        updateStatus();
        post(this::fitText);
    }

    /** Pesan di bawah ayat: galat/memuat audio (kuning), atau petunjuk ayat panjang (redup). */
    private void updateStatus() {
        String err = player.error() != null ? player.error() : player.isPreparing() ? "Memuat audio…" : null;
        String st = err != null ? err : overflow ? "Ayat panjang · ketuk ayat untuk membaca lengkap di mushaf" : null;
        status.setTextColor(err != null ? Ui.WARN : Ui.TEXT3);
        status.setText(st == null ? "" : st);
        status.setVisibility(st == null ? GONE : VISIBLE);
    }

    private static final float[][] FULL = {{32, 18}, {29, 17}, {26, 16}, {24, 15}};
    private static final float[][] COMPACT = {{28, 17}, {26, 16}, {24, 15}, {22, 15}};

    /**
     * Agar ayat panjang tidak terpotong di tengah baris: kecilkan teks bertahap; bila belum muat,
     * sembunyikan transliterasi; bila masih belum muat, tampilkan sebanyak baris yang muat dan akhiri dengan
     * "…" beserta petunjuk. Ayat lengkap ada di mushaf (ketuk ayat).
     */
    private void fitText() {
        int h = scroll.getHeight(), w = scroll.getWidth();
        if (h <= 0 || w <= 0) return;
        boolean wantLatin = Prefs.flag(getContext(), Prefs.SHOW_LATIN, true);
        boolean wantTranslation = Prefs.flag(getContext(), Prefs.SHOW_TRANSLATION, true);
        for (TextView t : new TextView[]{arabic, translation}) {
            t.setMaxLines(Integer.MAX_VALUE);
            t.setEllipsize(null);
        }
        latin.setVisibility(wantLatin ? VISIBLE : GONE);
        translation.setVisibility(wantTranslation ? VISIBLE : GONE);
        boolean fits = shrink(w, h, FULL);
        if (!fits) {
            latin.setVisibility(GONE);
            fits = shrink(w, h, COMPACT);
        }
        if (!fits) {
            // Ukuran sudah yang terkecil; batasi jumlah baris agar berakhir rapi dengan "…".
            int arabicH = body.getChildAt(0).getMeasuredHeight();
            int gap = ((LayoutParams) translation.getLayoutParams()).topMargin;
            if (!wantTranslation || arabicH + gap + translation.getLineHeight() > h) {
                translation.setVisibility(GONE);
                if (arabicH > h) clamp(arabic, h);
            } else {
                clamp(translation, h - arabicH - gap);
            }
        }
        boolean was = overflow;
        overflow = !fits;
        if (was != overflow) updateStatus();
    }

    private static void clamp(TextView t, int height) {
        t.setMaxLines(Math.max(1, (height - Ui.px(4)) / t.getLineHeight()));
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    private boolean shrink(int w, int h, float[][] steps) {
        for (float[] s : steps) {
            Ui.size(arabic, s[0]);
            Ui.size(latin, s[1]);
            Ui.size(translation, s[1]);
            body.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (body.getMeasuredHeight() <= h) return true;
        }
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        player.addListener(this);
        render();
    }

    @Override
    protected void onDetachedFromWindow() {
        player.removeListener(this);
        super.onDetachedFromWindow();
    }
}
