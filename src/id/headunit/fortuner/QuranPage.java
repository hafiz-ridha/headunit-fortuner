package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

/** Mushaf penuh di dalam launcher: daftar 114 surah dan ayat-ayat surah terpilih. */
final class QuranPage extends LinearLayout implements QuranPlayer.Listener {
    private final QuranRepository repo;
    private final QuranPlayer player;
    private final SurahAdapter surahAdapter = new SurahAdapter();
    private final AyahAdapter ayahAdapter = new AyahAdapter();
    private final ListView surahList;
    private final ListView ayahList;
    private final TextView title;
    private final TextView subtitle;
    private final ImageView playBtn;
    private final ImageView downloadBtn;
    private final TextView offlineInfo;
    private final TextView downloadAll;
    private int shownSurah;
    private final Runnable onDownload = this::refreshDownload;
    private List<QuranRepository.Ayah> ayat = Collections.emptyList();

    @SuppressLint("ViewConstructor")
    QuranPage(Context c) {
        super(c);
        repo = QuranRepository.get(c);
        player = QuranPlayer.get(c);
        setOrientation(HORIZONTAL);
        setPadding(Ui.px(16), Ui.px(6), Ui.px(22), Ui.px(14));

        LinearLayout left = Ui.column(c);
        left.setBackground(Ui.panel(20));
        left.setPadding(Ui.px(8), Ui.px(14), Ui.px(8), Ui.px(8));
        LinearLayout lh = Ui.row(c);
        lh.setPadding(Ui.px(12), 0, Ui.px(4), Ui.px(10));
        LinearLayout lhText = Ui.column(c);
        lhText.addView(Ui.text(c, "Daftar Surah", 20, Ui.TEXT, Ui.MEDIUM));
        offlineInfo = Ui.text(c, "", 13, Ui.TEXT2, Ui.REGULAR);
        lhText.addView(offlineInfo);
        ((LayoutParams) offlineInfo.getLayoutParams()).topMargin = Ui.px(5);
        lh.addView(lhText, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        downloadAll = Ui.text(c, "", 14, Ui.TEXT, Ui.MEDIUM);
        downloadAll.setGravity(Gravity.CENTER);
        downloadAll.setPadding(Ui.px(14), 0, Ui.px(14), 0);
        downloadAll.setBackground(Ui.rounded(0xFF1B2E48, 12));
        Ui.pressable(downloadAll);
        downloadAll.setOnClickListener(v -> toggleDownloadAll());
        lh.addView(downloadAll, new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.px(44)));
        left.addView(lh);
        surahList = list(c);
        surahList.setAdapter(surahAdapter);
        surahList.setOnItemClickListener((p, v, pos, id) -> showSurah(pos + 1, true));
        left.addView(surahList, Ui.weight(1f, false));
        addView(left, Ui.weight(0.3f, true));
        addView(Ui.space(c, 16, 1));

        LinearLayout right = Ui.column(c);
        right.setBackground(Ui.panel(20));
        right.setPadding(Ui.px(20), Ui.px(16), Ui.px(16), Ui.px(8));
        LinearLayout header = Ui.row(c);
        header.addView(Ui.iconView(c, Icons.medallion(), 48));
        header.addView(Ui.space(c, 14, 1));
        LinearLayout titles = Ui.column(c);
        title = Ui.text(c, "", 26, Ui.TEXT, Ui.MEDIUM);
        subtitle = Ui.text(c, "", 16, Ui.TEXT2, Ui.REGULAR);
        titles.addView(title);
        titles.addView(subtitle);
        ((LayoutParams) subtitle.getLayoutParams()).topMargin = Ui.px(6);
        header.addView(titles, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        playBtn = Ui.roundButton(c, R.drawable.ic_play, 52, 0xFF1B2E48, Ui.TEXT);
        playBtn.setOnClickListener(v -> {
            if (player.surah() != shownSurah) player.setPosition(shownSurah, 1);
            player.toggle();
        });
        downloadBtn = Ui.roundButton(c, R.drawable.ic_download, 52, 0xFF1B2E48, Ui.TEXT);
        downloadBtn.setContentDescription("Unduh murattal surah ini");
        downloadBtn.setOnClickListener(v -> QuranDownloadService.downloadSurah(getContext(), shownSurah));
        ImageView stop = Ui.roundButton(c, R.drawable.ic_stop, 52, 0xFF1B2E48, Ui.TEXT);
        stop.setContentDescription("Hentikan murattal");
        stop.setOnClickListener(v -> player.stop());
        header.addView(downloadBtn);
        header.addView(Ui.space(c, 12, 1));
        header.addView(playBtn);
        header.addView(Ui.space(c, 12, 1));
        header.addView(stop);
        right.addView(header);
        ayahList = list(c);
        ayahList.setAdapter(ayahAdapter);
        ayahList.setOnItemClickListener((p, v, pos, id) -> {
            player.setPosition(shownSurah, pos + 1);
            if (!player.isPlaying()) player.playCurrent();
        });
        LayoutParams alp = Ui.weight(1f, false);
        alp.topMargin = Ui.px(10);
        right.addView(ayahList, alp);
        addView(right, Ui.weight(0.7f, true));

        showSurah(player.surah(), false);
        ayahList.setSelection(Math.max(0, player.ayah() - 1));
        surahList.setSelection(Math.max(0, player.surah() - 3));
    }

    private ListView list(Context c) {
        ListView l = new ListView(c);
        l.setDivider(new ColorDrawable(0));
        l.setDividerHeight(Ui.px(6));
        l.setSelector(new ColorDrawable(0));
        l.setVerticalScrollBarEnabled(true);
        return l;
    }

    void showSurah(int number, boolean scrollTop) {
        QuranRepository.Surah s = repo.surah(number);
        if (s == null) return;
        shownSurah = number;
        ayat = s.ayat;
        title.setText(s.info.latin + "  ·  " + s.info.arabic);
        ayahAdapter.notifyDataSetChanged();
        if (scrollTop) ayahList.setSelection(0);
        updatePlay();
        refreshDownload();
    }

    /** Status murattal offline: tombol unduh per surah, keterangan di subjudul, dan ringkasan di daftar surah. */
    private void refreshDownload() {
        Context c = getContext();
        QuranRepository.SurahInfo info = repo.info(shownSurah);
        if (info == null) return;
        boolean complete = QuranAudioStore.isComplete(c, shownSurah);
        boolean busy = QuranDownloadService.running && QuranDownloadService.currentSurah == shownSurah;
        String audio = complete ? "Audio offline" : busy ? "Mengunduh audio…" : "Audio butuh internet";
        subtitle.setText("Surah ke-" + info.number + "  ·  " + info.ayahCount + " Ayat  ·  " + info.place
                + "  ·  " + info.meaning + "  ·  " + audio);
        downloadBtn.setVisibility(complete ? GONE : VISIBLE);
        downloadBtn.setAlpha(busy ? 0.5f : 1f);
        ((View) downloadBtn.getParent()).requestLayout();

        int done = QuranAudioStore.completeSurahs(c);
        if (QuranDownloadService.running) {
            int ayat = Math.max(0, QuranDownloadService.storedAyat);
            offlineInfo.setText(String.format(java.util.Locale.US, "Mengunduh %,d / %,d ayat", ayat, QuranAudioStore.TOTAL_AYAT)
                    .replace(',', '.'));
            downloadAll.setText("Jeda");
        } else {
            offlineInfo.setText(QuranDownloadService.lastError != null ? QuranDownloadService.lastError
                    : "Offline: " + done + " / 114 surah");
            downloadAll.setText(done == 114 ? "Lengkap" : "Unduh semua");
        }
        downloadAll.setEnabled(done < 114 || QuranDownloadService.running);
        surahAdapter.notifyDataSetChanged();
    }

    private void toggleDownloadAll() {
        Context c = getContext();
        if (QuranDownloadService.running) {
            QuranDownloadService.stop(c);
            return;
        }
        if (QuranAudioStore.completeSurahs(c) == 114) return;
        new android.app.AlertDialog.Builder(c).setTitle("Unduh semua murattal?")
                .setMessage("Seluruh 6.236 ayat (Misyari Rasyid Al-Afasi) sekitar 1,5–2 GB. Setelah selesai, "
                        + "Al-Qur'an bisa diputar tanpa internet. Bila memakai hotspot HP, kuota HP akan terpakai.")
                .setPositiveButton("Unduh", (d, w) -> QuranDownloadService.downloadAll(c))
                .setNegativeButton("Batal", null).show();
    }

    private void updatePlay() {
        boolean on = player.isPlaying() && player.surah() == shownSurah;
        playBtn.setImageDrawable(Ui.icon(getContext(), on ? R.drawable.ic_pause : R.drawable.ic_play, on ? Ui.GOLD : Ui.TEXT));
        playBtn.setContentDescription(on ? "Jeda murattal" : "Putar surah ini");
    }

    @Override
    public void onQuranChanged() {
        updatePlay();
        ayahAdapter.notifyDataSetChanged();
        if (player.isPlaying() && player.surah() == shownSurah) {
            int pos = player.ayah() - 1;
            if (pos < ayahList.getFirstVisiblePosition() || pos > ayahList.getLastVisiblePosition()) {
                ayahList.smoothScrollToPosition(pos);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        player.addListener(this);
        QuranDownloadService.addListener(onDownload);
        refreshDownload();
    }

    @Override
    protected void onDetachedFromWindow() {
        player.removeListener(this);
        QuranDownloadService.removeListener(onDownload);
        super.onDetachedFromWindow();
    }

    private final class SurahAdapter extends BaseAdapter {
        @Override public int getCount() { return repo.index().size(); }

        @Override public Object getItem(int i) { return repo.index().get(i); }

        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout row = (LinearLayout) convert;
            Context c = parent.getContext();
            if (row == null) {
                row = Ui.row(c);
                row.setPadding(Ui.px(10), Ui.px(12), Ui.px(12), Ui.px(12));
                ImageView badge = new ImageView(c);
                row.addView(badge, new LayoutParams(Ui.px(40), Ui.px(40)));
                LinearLayout texts = Ui.column(c);
                texts.addView(Ui.text(c, "", 19, Ui.TEXT, Ui.MEDIUM));
                TextView sub = Ui.text(c, "", 14, Ui.TEXT2, Ui.REGULAR);
                texts.addView(sub);
                ((LayoutParams) sub.getLayoutParams()).topMargin = Ui.px(5);
                LayoutParams tlp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
                tlp.leftMargin = Ui.px(12);
                row.addView(texts, tlp);
                TextView ar = Ui.text(c, "", 22, Ui.GOLD, Ui.arabic(c));
                ar.setIncludeFontPadding(true);
                row.addView(ar);
            }
            QuranRepository.SurahInfo s = repo.index().get(i);
            ((ImageView) row.getChildAt(0)).setImageDrawable(Icons.ayahBadge(s.number));
            LinearLayout texts = (LinearLayout) row.getChildAt(1);
            ((TextView) texts.getChildAt(0)).setText(s.latin);
            boolean offline = QuranAudioStore.isComplete(c, s.number);
            TextView sub = (TextView) texts.getChildAt(1);
            sub.setText(s.meaning + " · " + s.ayahCount + " ayat" + (offline ? " · offline" : ""));
            sub.setTextColor(offline ? Ui.GOLD : Ui.TEXT2);
            ((TextView) row.getChildAt(2)).setText(s.arabic);
            row.setBackground(s.number == shownSurah ? Ui.rounded(Ui.ACCENT_SOFT, 14) : null);
            return row;
        }
    }

    private final class AyahAdapter extends BaseAdapter {
        @Override public int getCount() { return ayat.size(); }

        @Override public Object getItem(int i) { return ayat.get(i); }

        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout cell = (LinearLayout) convert;
            Context c = parent.getContext();
            if (cell == null) {
                cell = Ui.column(c);
                cell.setPadding(Ui.px(16), Ui.px(14), Ui.px(16), Ui.px(16));
                LinearLayout top = Ui.row(c);
                top.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
                ImageView badge = new ImageView(c);
                top.addView(badge, new LayoutParams(Ui.px(42), Ui.px(42)));
                TextView ar = Ui.text(c, "", 32, Ui.TEXT, Ui.arabic(c));
                ar.setIncludeFontPadding(true);
                ar.setTextDirection(View.TEXT_DIRECTION_RTL);
                ar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                ar.setLineSpacing(Ui.px(10), 1f);
                LayoutParams alp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
                alp.leftMargin = Ui.px(14);
                top.addView(ar, alp);
                cell.addView(top);
                TextView lat = Ui.text(c, "", 18, 0xFFE8D7B0, Ui.REGULAR);
                lat.setLineSpacing(Ui.px(3), 1f);
                cell.addView(lat);
                ((LayoutParams) lat.getLayoutParams()).topMargin = Ui.px(12);
                TextView tr = Ui.text(c, "", 18, Ui.TEXT, Ui.REGULAR);
                tr.setLineSpacing(Ui.px(4), 1f);
                cell.addView(tr);
                ((LayoutParams) tr.getLayoutParams()).topMargin = Ui.px(6);
            }
            QuranRepository.Ayah a = ayat.get(i);
            LinearLayout top = (LinearLayout) cell.getChildAt(0);
            ((ImageView) top.getChildAt(0)).setImageDrawable(Icons.ayahBadge(a.number));
            ((TextView) top.getChildAt(1)).setText(a.arabic);
            TextView lat = (TextView) cell.getChildAt(1);
            TextView tr = (TextView) cell.getChildAt(2);
            lat.setText(a.latin);
            tr.setText(a.translation);
            lat.setVisibility(Prefs.flag(c, Prefs.SHOW_LATIN, true) ? VISIBLE : GONE);
            tr.setVisibility(Prefs.flag(c, Prefs.SHOW_TRANSLATION, true) ? VISIBLE : GONE);
            boolean current = player.surah() == shownSurah && player.ayah() == a.number;
            cell.setBackground(current ? Ui.rounded(player.isPlaying() ? 0x33D4AF6A : Ui.ACCENT_SOFT, 14)
                    : Ui.rounded(0x14FFFFFF, 14));
            return cell;
        }
    }
}
