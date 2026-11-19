package com.github.stenzek.duckstation;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** FarsiPSX PS1 launcher: reads config.json, downloads game data and boots DuckStation. */
public class FarsiLauncherActivity extends Activity {
    private static final String GAME_ACTION = "farsipsx.GAME";
    private static final String MARKET_PACKAGE = "com.farsitel.bazaar";
    private static final String BIOS_MAGIC = "FPSXBIOS";
    private static final String SEED_NAME = "manifest.obb";
    private static final String CONFIG_URL = "https://newmshop.ir/config.json";

    private static class Srv {
        String url; int priority;
        Srv(String u,int p){url=u;priority=p;}
    }
    private static class Game {
        String pkg,name,console,icon,cover,marketUrl,gameUrl,biosUrl,bios;
        int dataVersion;
        Game(JSONObject o){
            pkg=o.optString("package","");
            name=o.optString("name",pkg);
            console=o.optString("console","PS1").toUpperCase();
            icon=o.optString("icon","");
            cover=o.optString("cover","");
            marketUrl=o.optString("marketUrl","bazaar://details?id="+pkg);
            dataVersion=o.optInt("dataVersion",1);
            gameUrl=o.optString("gameUrl","");
            biosUrl=o.optString("biosUrl","");
            bios=o.optString("bios","");
        }
    }
    private static class Seed {
        String gameUrl,biosUrl,bios,console;
        Seed(String g,String b,String bi,String c){gameUrl=g;biosUrl=b;bios=bi;console=c;}
    }
    private interface ProgressCb { void onProgress(long done,long total); }
    private interface ImageCb { void onBitmap(Bitmap b); }

    private LinearLayout root,row,storeList;
    private View homeView,storeView;
    private TextView badge,titleText;
    private ImageView coverImage;
    private Button playButton,downloadButton;
    private JSONObject cfg=new JSONObject();
    private List<Game> games=new ArrayList<>();
    private List<Srv> servers=new ArrayList<>();
    private Game mine;
    private final List<String> installed=new ArrayList<>();
    private String storeFilter="ALL";
    private final Map<String,Bitmap> bitmaps=new HashMap<>();

    private int dip(int v){
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,v,getResources().getDisplayMetrics()));
    }
    private GradientDrawable pill(String color){
        GradientDrawable d=new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(dip(20));
        return d;
    }
    private Button actionButton(String text,String color,View.OnClickListener click){
        Button b=new Button(this);
        b.setText(text); b.setTextSize(16); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE); b.setBackground(pill(color)); b.setOnClickListener(click);
        return b;
    }
    private void show(View v){
        homeView.setVisibility(v==homeView?View.VISIBLE:View.GONE);
        storeView.setVisibility(v==storeView?View.VISIBLE:View.GONE);
    }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#08080A"));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);
        try{
            for(android.content.pm.ResolveInfo ri:getPackageManager().queryIntentActivities(new Intent(GAME_ACTION),0))
                installed.add(ri.activityInfo.packageName);
        }catch(Exception ignored){}
        buildHome(); buildStore(); show(homeView);
        new Thread(this::loadConfig).start();
    }

    private void buildHome(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#08080A"));
        LinearLayout col=new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dip(24),dip(28),dip(24),dip(24));
        scroll.addView(col,new ViewGroup.LayoutParams(-1,-1));
        homeView=scroll;
        root.addView(homeView,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=new TextView(this);
        logo.setText("FarsiPSX"); logo.setTextSize(26); logo.setTextColor(Color.WHITE);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        badge=new TextView(this);
        badge.setText("PS1"); badge.setTextSize(13); badge.setTextColor(Color.WHITE);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dip(9),dip(4),dip(9),dip(4)); badge.setBackground(pill("#E60012"));
        Button store=new Button(this);
        store.setText("فروشگاه"); store.setTextSize(14); store.setAllCaps(false);
        store.setTextColor(Color.WHITE); store.setBackground(pill("#26262E"));
        store.setOnClickListener(v->{show(storeView);renderStore();});
        header.addView(logo);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2); bp.leftMargin=dip(10);
        header.addView(badge,bp);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2); sp.setMarginStart(dip(12));
        header.addView(store,sp);
        col.addView(header);

        coverImage=new ImageView(this);
        coverImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverImage.setBackgroundColor(Color.parseColor("#191920"));
        coverImage.setAdjustViewBounds(true);
        LinearLayout.LayoutParams cip=new LinearLayout.LayoutParams(-1,dip(210)); cip.topMargin=dip(22);
        col.addView(coverImage,cip);

        titleText=new TextView(this);
        titleText.setText(getApplicationInfo().loadLabel(getPackageManager()));
        titleText.setTextSize(24); titleText.setTextColor(Color.WHITE);
        titleText.setTypeface(Typeface.DEFAULT_BOLD); titleText.setGravity(Gravity.CENTER);
        titleText.setMaxLines(2); titleText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tip=new LinearLayout.LayoutParams(-1,-2); tip.topMargin=dip(16);
        col.addView(titleText,tip);

        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER);
        playButton=actionButton("اجرا","#E60012",v->playGame());
        downloadButton=actionButton("دانلود","#1D7A3D",v->downloadGame());
        bar.addView(playButton,new LinearLayout.LayoutParams(dip(150),dip(54)));
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(dip(150),dip(54)); dlp.setMarginStart(dip(12));
        bar.addView(downloadButton,dlp);
        LinearLayout.LayoutParams barp=new LinearLayout.LayoutParams(-1,-2); barp.topMargin=dip(18);
        col.addView(bar,barp);

        TextView rowTitle=new TextView(this);
        rowTitle.setText("بازیهای دیگر"); rowTitle.setTextSize(15);
        rowTitle.setTextColor(Color.parseColor("#8B8B95"));
        rowTitle.setPadding(0,dip(24),0,dip(8));
        col.addView(rowTitle);
        HorizontalScrollView hs=new HorizontalScrollView(this);
        row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0,dip(4),0,dip(12));
        hs.addView(row);
        col.addView(hs,new LinearLayout.LayoutParams(-1,dip(150)));
    }

    private void buildStore(){
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#08080A"));
        LinearLayout col=new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dip(24),dip(28),dip(24),dip(24));
        scroll.addView(col,new ViewGroup.LayoutParams(-1,-1));
        storeView=scroll;
        root.addView(storeView,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);
        title.setText("فروشگاه"); title.setTextSize(24); title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        Button back=new Button(this);
        back.setText("بازگشت"); back.setTextSize(13); back.setAllCaps(false);
        back.setTextColor(Color.WHITE); back.setBackground(pill("#26262E"));
        back.setOnClickListener(v->show(homeView));
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1f)); head.addView(back);
        col.addView(head);

        LinearLayout filters=new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0,dip(14),0,dip(10));
        for(final String f:Arrays.asList("ALL","PS1","PS2")){
            Button b=new Button(this);
            b.setText(f); b.setTextSize(13); b.setAllCaps(false);
            b.setTextColor(Color.WHITE); b.setBackground(pill(f.equals(storeFilter)?"#E60012":"#26262E"));
            b.setOnClickListener(v->{storeFilter=f;renderStore();});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dip(88),dip(44)); lp.setMarginEnd(dip(8));
            filters.addView(b,lp);
        }
        col.addView(filters);
        storeList=new LinearLayout(this); storeList.setOrientation(LinearLayout.VERTICAL);
        col.addView(storeList,new LinearLayout.LayoutParams(-1,-2));
    }

    private View gameCard(Game g){
        boolean installedNow=installed.contains(g.pkg)||g.pkg.equals(getPackageName());
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dip(6),dip(6),dip(6),dip(6));
        card.setAlpha(installedNow?1f:0.35f);
        card.setBackground(pill("#17171D"));
        card.setOnClickListener(v->openMarket(g));
        ImageView img=new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(Color.parseColor("#22222A"));
        card.addView(img,new LinearLayout.LayoutParams(dip(116),dip(76)));
        TextView label=new TextView(this);
        label.setText(g.name); label.setTextSize(12); label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER); label.setMaxLines(2); label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dip(116),-2); lp.topMargin=dip(6);
        card.addView(label,lp);
        loadImage(g.icon,img::setImageBitmap);
        return card;
    }

    private void renderStore(){
        storeList.removeAllViews();
        List<Game> list=new ArrayList<>();
        for(Game g:games) if(storeFilter.equals("ALL")||g.console.equals(storeFilter)) list.add(g);
        for(Game g:list){
            LinearLayout rowView=new LinearLayout(this);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(Gravity.CENTER_VERTICAL);
            rowView.setPadding(dip(10),dip(10),dip(10),dip(10));
            rowView.setBackground(pill("#14141A"));
            rowView.setAlpha((installed.contains(g.pkg)||g.pkg.equals(getPackageName()))?1f:0.35f);
            rowView.setOnClickListener(v->openMarket(g));
            ImageView img=new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackgroundColor(Color.parseColor("#22222A"));
            rowView.addView(img,new LinearLayout.LayoutParams(dip(62),dip(62)));
            TextView tx=new TextView(this);
            tx.setText(g.name+"   •   "+g.console); tx.setTextSize(15); tx.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1f); tp.setMarginStart(dip(12));
            rowView.addView(tx,tp);
            loadImage(g.icon,img::setImageBitmap);
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.bottomMargin=dip(8);
            storeList.addView(rowView,rp);
        }
        if(list.isEmpty()){
            TextView t=new TextView(this); t.setText("—"); t.setTextColor(Color.WHITE); t.setGravity(Gravity.CENTER);
            storeList.addView(t);
        }
    }

    private String httpGet(String url){
        HttpURLConnection conn=null;
        try{
            conn=(HttpURLConnection)new URL(url).openConnection();
            conn.setConnectTimeout(12000); conn.setReadTimeout(20000);
            try(InputStream in=conn.getInputStream()){
                ByteArrayOutputStream bo=new ByteArrayOutputStream();
                byte[] buf=new byte[8192]; int n;
                while((n=in.read(buf))>0)bo.write(buf,0,n);
                return new String(bo.toByteArray(),StandardCharsets.UTF_8);
            }
        }catch(Exception e){return null;}
        finally{if(conn!=null)conn.disconnect();}
    }

    private void loadConfig(){
        String text=httpGet(CONFIG_URL);
        if(text==null){runOnUiThread(()->toast("اتصال به پنل برقرار نشد"));return;}
        try{cfg=new JSONObject(text);}catch(Exception e){runOnUiThread(()->toast("config.json نامعتبر"));return;}
        List<Game> parsed=new ArrayList<>();
        JSONArray arr=cfg.optJSONArray("games");
        if(arr!=null)for(int i=0;i<arr.length();i++){
            JSONObject o=arr.optJSONObject(i); if(o==null)continue;
            String p=o.optString("package","");
            if(p.isEmpty()||!o.optBoolean("visible",true))continue;
            parsed.add(new Game(o));
        }
        List<Srv> sp=new ArrayList<>();
        JSONArray sarr=cfg.optJSONArray("servers");
        if(sarr!=null)for(int i=0;i<sarr.length();i++){
            JSONObject o=sarr.optJSONObject(i); if(o==null)continue;
            String u=o.optString("url","");
            if(!u.isEmpty()&&o.optBoolean("enabled",true))sp.add(new Srv(u,o.optInt("priority",99)));
        }
        final List<Game> fg=parsed; final List<Srv> fs=sp;
        runOnUiThread(()->{
            games=fg;
            List<Srv> sorted=new ArrayList<>(fs);
            sorted.sort(Comparator.comparingInt(s->s.priority));
            servers=sorted;
            mine=null;
            for(Game g:games) if(g.pkg.equals(getPackageName())){mine=g;break;}
            renderHome(); renderStore(); checkMarketLock();
        });
    }

    private void renderHome(){
        if(mine==null){
            titleText.setText(getApplicationInfo().loadLabel(getPackageManager()));
            badge.setText("PS1"); toast("بازی در config پیدا نشد"); return;
        }
        titleText.setText(mine.name); badge.setText(mine.console);
        loadImage(mine.cover,coverImage::setImageBitmap);
        row.removeAllViews();
        for(Game g:games) if(!g.pkg.equals(getPackageName())) row.addView(gameCard(g));
        File f=dataFile(mine);
        boolean ready=f.exists()&&f.length()>(16L<<20);
        playButton.setEnabled(ready); playButton.setAlpha(ready?1f:0.45f);
    }

    private void loadImage(final String url,final ImageCb cb){
        if(url==null||url.trim().isEmpty())return;
        if(bitmaps.containsKey(url)){cb.onBitmap(bitmaps.get(url));return;}
        new Thread(()->{
            Bitmap b=null; HttpURLConnection conn=null;
            try{
                conn=(HttpURLConnection)new URL(url).openConnection();
                conn.setConnectTimeout(8000); conn.setReadTimeout(12000);
                try(InputStream in=conn.getInputStream()){b=BitmapFactory.decodeStream(in);}
            }catch(Exception ignored){}
            finally{if(conn!=null)conn.disconnect();}
            if(b!=null){bitmaps.put(url,b);runOnUiThread(()->cb.onBitmap(b));}
        }).start();
    }

    private void checkMarketLock(){
        JSONObject app=cfg.optJSONObject("appearance");
        boolean lock=app==null||app.optBoolean("marketLock",true);
        if(!lock)return;
        String installer=null;
        try{
            if(Build.VERSION.SDK_INT>=30) installer=getPackageManager().getInstallSourceInfo(getPackageName()).getInstallingPackageName();
            else installer=getPackageManager().getInstallerPackageName(getPackageName());
        }catch(Exception ignored){}
        if(MARKET_PACKAGE.equals(installer))return;
        String msg=null; JSONObject msgs=cfg.optJSONObject("messages");
        if(msgs!=null)msg=msgs.optString("sideload","");
        if(msg==null||msg.isEmpty())msg="لطفاً بازی را از کافه بازار بخرید. ممنون از حمایتتان.";
        new AlertDialog.Builder(this).setTitle("نیاز به خرید").setMessage(msg)
            .setPositiveButton("بازار",(d,w)->{if(mine!=null)openMarket(mine);})
            .setNegativeButton("بستن",null).show();
    }

    private void openMarket(Game g){
        try{
            Uri uri=Uri.parse(g.marketUrl==null||g.marketUrl.isEmpty()?"bazaar://details?id="+g.pkg:g.marketUrl);
            Intent i=new Intent(Intent.ACTION_VIEW,uri); i.setPackage(MARKET_PACKAGE);
            startActivity(i);
        }catch(Exception e){
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://cafebazaar.ir/app/"+g.pkg)));
        }
    }

    private File dataFile(Game g){
        int code=1;
        try{code=getPackageManager().getPackageInfo(getPackageName(),0).versionCode;}catch(Exception ignored){}
        File dir;
        try{dir=getObbDir();}catch(Exception e){dir=null;}
        if(dir==null){
            File ext=getExternalFilesDir(null);
            dir=new File(ext==null?getFilesDir():ext,"obb");
        }
        return new File(dir,"main."+code+"."+getPackageName()+".obb");
    }

    private String serverUrl(Srv s,Game g,String name){
        String base=s.url.replace("{package}",g.pkg).replace("{dataVersion}",String.valueOf(g.dataVersion));
        return base.endsWith("/")?base+name:base+"/"+name;
    }

    private boolean fetchToFile(String url,File target,ProgressCb cb){
        HttpURLConnection conn=null;
        try{
            conn=(HttpURLConnection)new URL(url).openConnection();
            conn.setConnectTimeout(15000); conn.setReadTimeout(60000);
            long total=Math.max(conn.getContentLengthLong(),1L);
            File parent=target.getParentFile(); if(parent!=null)parent.mkdirs();
            try(InputStream in=conn.getInputStream(); FileOutputStream out=new FileOutputStream(target)){
                byte[] buf=new byte[65536]; int n; long done=0;
                while((n=in.read(buf))>0){out.write(buf,0,n);done+=n;if(cb!=null)cb.onProgress(done,total);}
            }
            return target.exists();
        }catch(Exception e){return false;}
        finally{if(conn!=null)conn.disconnect();}
    }

    private boolean isFinalObb(File f){
        try{
            if(!f.exists()||f.length()<(16L<<20))return false;
            try(RandomAccessFile raf=new RandomAccessFile(f,"r")){
                byte[] tail=new byte[8];
                raf.seek(raf.length()-8); raf.readFully(tail);
                return BIOS_MAGIC.equals(new String(tail,StandardCharsets.US_ASCII));
            }
        }catch(Exception e){return false;}
    }

    private Seed readSeed(File f){
        try{
            if(!f.exists()||f.length()>(2L<<20))return null;
            String text=readText(f).trim();
            if(!text.startsWith("{"))return null;
            JSONObject o=new JSONObject(text);
            Seed s=new Seed(o.optString("gameUrl",""),o.optString("biosUrl",""),o.optString("bios",""),o.optString("console",""));
            if(s.gameUrl.isEmpty()&&s.biosUrl.isEmpty()&&s.bios.isEmpty())return null;
            return s;
        }catch(Exception e){return null;}
    }

    private String readText(File f)throws Exception{
        try(InputStream in=new FileInputStream(f)){
            ByteArrayOutputStream bo=new ByteArrayOutputStream();
            byte[] buf=new byte[8192]; int n;
            while((n=in.read(buf))>0)bo.write(buf,0,n);
            return new String(bo.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private void writeText(File f,String s)throws Exception{
        File parent=f.getParentFile(); if(parent!=null)parent.mkdirs();
        try(FileOutputStream out=new FileOutputStream(f)){out.write(s.getBytes(StandardCharsets.UTF_8));}
    }
    private void copy(File from,File to){
        try(InputStream in=new FileInputStream(from); FileOutputStream out=new FileOutputStream(to)){
            byte[] buf=new byte[65536]; int n; while((n=in.read(buf))>0)out.write(buf,0,n);
        }catch(Exception ignored){}
    }

    private Seed ensureSeed(Game g,File obb){
        Seed s=readSeed(obb); if(s!=null)return s;
        File parent=obb.getParentFile(); if(parent!=null)parent.mkdirs();
        try(InputStream in=getAssets().open("seed.obb")){
            File tmp=new File(obb.getPath()+".seed");
            try(FileOutputStream out=new FileOutputStream(tmp)){
                byte[] buf=new byte[65536]; int n; while((n=in.read(buf))>0)out.write(buf,0,n);
            }
            s=readSeed(tmp); if(s!=null){copy(tmp,obb);tmp.delete();return s;} tmp.delete();
        }catch(Exception ignored){}
        for(Srv srv:servers){
            try{
                File tmp=new File(obb.getPath()+".seed");
                if(fetchToFile(serverUrl(srv,g,SEED_NAME),tmp,null)){
                    s=readSeed(tmp); if(s!=null){copy(tmp,obb);tmp.delete();return s;}
                }
                tmp.delete();
            }catch(Exception ignored){}
        }
        Seed fromConfig=new Seed(g.gameUrl,g.biosUrl,g.bios,g.console);
        if(!fromConfig.gameUrl.isEmpty()||!fromConfig.biosUrl.isEmpty()||!fromConfig.bios.isEmpty()){
            try{
                JSONObject o=new JSONObject();
                o.put("gameUrl",fromConfig.gameUrl); o.put("biosUrl",fromConfig.biosUrl);
                o.put("bios",fromConfig.bios); o.put("console",fromConfig.console);
                writeText(obb,o.toString());
            }catch(Exception ignored){}
            return fromConfig;
        }
        return null;
    }

    private byte[] biosBytes(Seed s){
        if(s.bios!=null&&!s.bios.isEmpty()){
            try{return Base64.decode(s.bios,Base64.DEFAULT);}catch(Exception ignored){}
        }
        if(s.biosUrl!=null&&!s.biosUrl.isEmpty()){
            HttpURLConnection conn=null;
            try{
                conn=(HttpURLConnection)new URL(s.biosUrl).openConnection();
                conn.setConnectTimeout(15000); conn.setReadTimeout(60000);
                try(InputStream in=conn.getInputStream()){
                    ByteArrayOutputStream bo=new ByteArrayOutputStream();
                    byte[] buf=new byte[65536]; int n; while((n=in.read(buf))>0)bo.write(buf,0,n);
                    return bo.toByteArray();
                }
            }catch(Exception ignored){}
            finally{if(conn!=null)conn.disconnect();}
        }
        return null;
    }

    private void writeFinalObb(File game,byte[] bios,File obb){
        File tmp=new File(obb.getPath()+".part");
        final byte[] b=bios==null?new byte[0]:bios;
        try(FileOutputStream out=new FileOutputStream(tmp)){
            try(InputStream in=new FileInputStream(game)){
                byte[] buf=new byte[65536]; int n; while((n=in.read(buf))>0)out.write(buf,0,n);
            }
            out.write(b);
            byte[] len=new byte[8]; long v=b.length;
            for(int i=0;i<8;i++){len[i]=(byte)(v&0xFF);v>>=8;}
            out.write(len); out.write(BIOS_MAGIC.getBytes(StandardCharsets.US_ASCII));
        }catch(Exception ignored){}
        if(obb.exists())obb.delete();
        if(!tmp.renameTo(obb)){copy(tmp,obb);tmp.delete();}
    }

    private void downloadGame(){
        final Game g=mine; if(g==null)return;
        final ProgressDialog pd=new ProgressDialog(this);
        pd.setTitle("دانلود"); pd.setMessage(g.name);
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL); pd.setMax(100); pd.setCancelable(false); pd.show();
        new Thread(()->{
            boolean ok=false; String last="";
            try{
                File obb=dataFile(g);
                if(isFinalObb(obb)){runOnUiThread(()->{pd.dismiss();toast("دانلود کامل شد");renderHome();});return;}
                Seed seed=ensureSeed(g,obb);
                if(seed==null)throw new IllegalStateException("فایل راهنما پیدا نشد");
                File gameTmp=new File(obb.getPath()+".game");
                boolean got=false;
                if(seed.gameUrl!=null&&!seed.gameUrl.isEmpty()){
                    got=fetchToFile(seed.gameUrl,gameTmp,(d,t)->runOnUiThread(()->pd.setProgress((int)(d*100/t))));
                }else{
                    for(Srv srv:servers){
                        got=fetchToFile(serverUrl(srv,g,"game.obb"),gameTmp,(d,t)->runOnUiThread(()->pd.setProgress((int)(d*100/t))));
                        if(got)break;
                    }
                }
                if(!got||gameTmp.length()<(16L<<20))throw new IllegalStateException("فایل بازی دانلود نشد");
                byte[] bios=biosBytes(seed);
                writeFinalObb(gameTmp,bios,obb);
                gameTmp.delete();
                ok=obb.exists()&&obb.length()>(16L<<20);
            }catch(Exception e){last=e.getMessage()==null?"خطا":e.getMessage();}
            final boolean fok=ok; final String flast=last;
            runOnUiThread(()->{pd.dismiss();toast(fok?"دانلود کامل شد":"دانلود ناموفق: "+flast);renderHome();});
        }).start();
    }

    /** Strip the optional BIOS tail and write a real cue/bin for DuckStation. */
    private File prepareGame(File obb){
        try{
            File ext=getExternalFilesDir(null);
            File dir=new File(ext==null?getFilesDir():ext,"psx");
            dir.mkdirs();
            File bin=new File(dir,"game.bin");
            File cue=new File(dir,"game.cue");
            long gameSize=obb.length();
            if(isFinalObb(obb)){
                try(RandomAccessFile raf=new RandomAccessFile(obb,"r")){
                    long len=raf.length(); raf.seek(len-16);
                    byte[] tail=new byte[16]; raf.readFully(tail);
                    long biosSize=0;
                    for(int i=7;i>=0;i--)biosSize=(biosSize<<8)|(tail[i]&0xFFL);
                    gameSize=len-16-biosSize;
                }
            }
            if(gameSize<(16L<<20))return null;
            if(!bin.isFile()||bin.length()!=gameSize){
                try(RandomAccessFile in=new RandomAccessFile(obb,"r"); FileOutputStream out=new FileOutputStream(bin)){
                    byte[] buf=new byte[262144]; long left=gameSize;
                    while(left>0){
                        int n=in.read(buf,0,(int)Math.min(buf.length,left));
                        if(n<=0)break;
                        out.write(buf,0,n); left-=n;
                    }
                }
            }
            writeText(cue,"FILE \"game.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n");
            return cue;
        }catch(Exception e){return null;}
    }

    private void playGame(){
        Game g=mine; if(g==null)return;
        File f=dataFile(g);
        if(!f.exists()){toast("دیتای بازی وجود ندارد");return;}
        File cue=prepareGame(f);
        if(cue==null){toast("فایل دیتای بازی معتبر نیست");return;}
        try{
            Intent intent=new Intent(this,EmulationActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.putExtra("bootPath",cue.getAbsolutePath());
            intent.putExtra("resumeState",false);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }catch(Throwable t){toast("خطا در اجرای شبیهساز: "+t.getMessage());}
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
