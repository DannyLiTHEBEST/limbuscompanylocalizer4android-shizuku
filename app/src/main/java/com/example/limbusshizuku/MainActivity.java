package com.example.limbusshizuku;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
  private static final String GAME="com.ProjectMoon.LimbusCompany";
  private static final String DEST="/sdcard/Android/data/"+GAME+"/files/Assets/Resources_moved/Localize/jp";
  private static final String TMP="/data/local/tmp/limbus-shizuku-jp-update.zip";
  private static final int REQUEST_SHIZUKU=18027;
  private TextView status; private Button before, after, update;
  private PatchRepository patches;
  private final Shizuku.OnRequestPermissionResultListener permissionListener=(code,result)->{if(code==REQUEST_SHIZUKU){ if(result==PackageManager.PERMISSION_GRANTED) show("Shizuku 已授权，可以点击覆盖按钮。");else show("Shizuku 授权被拒绝；请在 Shizuku 中授予本应用权限。");}};
  @Override public void onCreate(Bundle state){ super.onCreate(state);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,30,28,20);root.setBackgroundColor(0xff202833);
    ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    text(root,"边狱巴士 · Shizuku 汉化助手",22,Color.WHITE);
    text(root,"自动核对游戏版本与 GitHub 正式版 jp.zip · 支持 Shizuku root / ADB",14,0xffcbdbe8);
    patches=new PatchRepository(this);
    update=button(root,"联网检查 / 更新汉化包");update.setOnClickListener(v->updatePatch());
    Button auth=button(root,"检查 / 申请 Shizuku 授权");auth.setOnClickListener(v->checkAuth());
    before=button(root,"① 预覆盖（游戏完全退出时）");before.setOnClickListener(v->execute(false));
    text(root,"然后启动游戏，等待约 25.4 MB 下载完且标题页重新出现，按 Home 退后台。",14,0xffcbdbe8);
    after=button(root,"② 下载后覆盖（游戏存活且在后台）");after.setOnClickListener(v->execute(true));
    text(root,"覆盖成功后从最近任务切回原游戏。不要划掉游戏任务。",14,0xffcbdbe8);
    Button open=button(root,"打开游戏");open.setOnClickListener(v->{try{Intent i=getPackageManager().getLaunchIntentForPackage(GAME);if(i==null)throw new Exception("找不到游戏，请确认已安装 Play 版");startActivity(i);}catch(Exception e){show(e.getMessage());}});
    status=text(root,"准备就绪。第一次请先授权 Shizuku。",15,0xffdceefc);
    Shizuku.addRequestPermissionResultListener(permissionListener);
  }
  @Override protected void onDestroy(){Shizuku.removeRequestPermissionResultListener(permissionListener);super.onDestroy();}
  private TextView text(LinearLayout parent,String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,8,0,16);parent.addView(t);return t;}
  private Button button(LinearLayout parent,String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);parent.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
  private void show(String s){status.setText(s);}
  private boolean authorized(){if(!Shizuku.pingBinder()){show("Shizuku 未运行。请先在 Shizuku 应用中启动服务。");return false;}try{int uid=Shizuku.getUid();if(uid!=0&&uid!=2000){show("Shizuku 服务身份异常（uid="+uid+"）；仅接受 root 或 ADB shell 模式。");return false;}if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){Shizuku.requestPermission(REQUEST_SHIZUKU);show("正在等待 Shizuku 授权弹窗…");return false;}return true;}catch(Exception e){show("连接 Shizuku 失败："+e.getMessage());return false;}}
  private void checkAuth(){if(authorized()){int uid=Shizuku.getUid();show("Shizuku 已授权，当前为"+(uid==0?"root":"ADB shell")+"模式（uid="+uid+"）。能否覆盖以实际写入权限检查为准。");}}
  private void setBusy(boolean busy){before.setEnabled(!busy);after.setEnabled(!busy);update.setEnabled(!busy);}
  private String gameVersion()throws Exception{
    String v=getPackageManager().getPackageInfo(GAME,0).versionName;
    if(v==null||!v.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IOException("未安装支持版本的游戏："+v);
    return v;
  }
  private void updatePatch(){setBusy(true);show("正在联网核对游戏版本与上游正式补丁…");new AsyncTask<Void,Void,String>(){
    protected String doInBackground(Void... v){try{
      String game=gameVersion();String latest=patches.latestTag();
      if(!latest.equals(game)){
        try{PatchRepository.Patch compatible=patches.download(game);return "上游最新正式版 v"+latest+"，当前游戏 v"+game+"。已另行下载并验证与当前游戏匹配的补丁 v"+compatible.version+"；不会跨版本覆盖。";}
        catch(Exception ex){return "上游最新正式版 v"+latest+" 与当前游戏 v"+game+" 不同，且无法取得当前游戏版本的正式补丁："+ex.getMessage()+"。未更换缓存；请先更新游戏或使用已验证的同版本本地包。";}
      }
      PatchRepository.Patch cached=patches.cached(game);
      PatchRepository.Patch patch=patches.download(game);
      return "✓ "+patch.source+" v"+game+"（SHA-256 "+patch.sha.substring(0,12)+"…）";
    }catch(Exception e){return "联网检查/下载失败："+e.getMessage()+"。仍可使用与游戏版本相同且已校验的本地补丁；不能跨版本覆盖。";}}
    protected void onPostExecute(String result){setBusy(false);show(result);}
  }.execute();}
  private void execute(boolean post){if(!authorized())return;setBusy(true);show("正在检查游戏版本、汉化资源和文件权限…");new AsyncTask<Void,Void,String>(){
    protected String doInBackground(Void... v){try{return patch(post);}catch(Exception e){return "失败："+e.toString();}}
    protected void onPostExecute(String result){show(result);setBusy(false);}
  }.execute();}
  private static String sq(String s){return "'"+s.replace("'","'\\''")+"'";}
  private static String digest(InputStream in)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];int n;while((n=in.read(b))>=0)md.update(b,0,n);StringBuilder s=new StringBuilder();for(byte c:md.digest())s.append(String.format(Locale.ROOT,"%02x",c&255));return s.toString();}
  private String patch(boolean post)throws Exception{
    int uid=Shizuku.getUid();if(uid!=0&&uid!=2000)throw new IOException("Shizuku 身份异常（uid="+uid+"）");
    String version=gameVersion();
    // In the narrow post-download window use the verified local cache, never block on network.
    PatchRepository.Patch selected=patches.cached(version);String note="";
    if(!post){
      try{
        String newest=patches.latestTag();
        if(!newest.equals(version))note="上游最新为 v"+newest+"；尝试查找当前游戏 v"+version+" 的专用补丁。";
        selected=patches.download(version);
      }catch(Exception e){note="联网获取匹配补丁失败："+e.getMessage()+"；改用已验证的同版本本地资源。";}
    }
    if(selected==null && version.equals(PatchRepository.BUILTIN_VERSION))selected=patches.bundled(version);
    if(selected==null && post)throw new IOException("本地没有匹配游戏 v"+version+" 的已验证补丁；请先在游戏退出时点击联网检查并缓存，不能在下载后窗口临时联网。");
    if(selected==null)throw new IOException("找不到与游戏 v"+version+" 匹配且验证通过的汉化包；不会跨版本覆盖。"+note);
    String base="set -eu\nGAME="+sq(GAME)+"\nDEST="+sq(DEST)+"\nTMP="+sq(TMP)+"\n";
    String check=base+
      "V=$(dumpsys package \"$GAME\" | sed -n 's/^[[:space:]]*versionName=//p' | head -n 1)\n"+
      "[ \"$V\" = "+sq(version)+" ] || { echo '游戏版本在操作中变化，请重试'; exit 2; }\n"+
      (post?"pidof \"$GAME\" >/dev/null || { echo '游戏进程不存在；请先启动游戏并完成下载'; exit 2; }\n"+
             "if dumpsys activity activities | grep 'topResumedActivity=' | tail -1 | grep -F \"$GAME\" >/dev/null; then echo '游戏仍在前台，请先按 Home'; exit 2; fi\n":
             "if pidof \"$GAME\" >/dev/null; then echo '预覆盖前请彻底退出游戏并划掉游戏任务'; exit 2; fi\n")+
      "[ -d \"$DEST\" ] || { echo '找不到游戏日语目录，请先在游戏内选择日本語并下载资源'; exit 2; }\n"+
      "[ -w \"$DEST\" ] || { echo '当前 Shizuku 服务无法写入游戏 Android/data 目录；请检查运行模式和设备权限'; exit 3; }\n"+
      "testfile=\"$DEST/.shizuku-write-test-$$\"\n"+
      "( umask 077; : > \"$testfile\" ) || { echo '实际写入游戏目录失败；请检查 Android/data 权限'; exit 3; }\n"+
      "rm -f \"$testfile\"\n"+
      "echo '版本、进程和目录权限检查通过'\n";
    Result c=run(check);if(c.code!=0)return "✗ "+c.output;
    Result streamed=streamZip(selected.file);if(streamed.code!=0)return "✗ 资源传输失败："+streamed.output;
    String apply=base+"trap 'rm -f \"$TMP\"' EXIT\n"+
      "[ \"$(sha256sum \"$TMP\" | cut -d ' ' -f 1)\" = "+sq(selected.sha)+" ] || { echo '传输后校验失败'; exit 4; }\n"+
      "unzip -oq \"$TMP\" -d /sdcard || { echo '解压失败；部分文件可能已经写入'; exit 4; }\n"+
      "[ \"$(sha256sum \"$DEST/JP_MainUIText.json\" | cut -d ' ' -f 1)\" = "+sq(selected.ui)+" ] || { echo '主界面文件验证失败'; exit 4; }\n"+
      "[ \"$(sha256sum \"$DEST/JP_BattleKeywords.json\" | cut -d ' ' -f 1)\" = "+sq(selected.keywords)+" ] || { echo '战斗文件验证失败'; exit 4; }\n"+
      "echo '已验证关键汉化文件'\n";
    Result r=run(apply);if(r.code!=0)return "✗ "+r.output;
    return "✓ 使用"+selected.source+" v"+version+"；"+r.output+(note.isEmpty()?"":"\n"+note)+(post?"\n请从最近任务切回原游戏，不要重新启动。":"\n现在打开游戏；下载完成、标题页重新显示后按 Home，再点②。");
  }
  private static class Result{final int code;final String output;Result(int c,String s){code=c;output=s;}}
  private Process proc(String script)throws Exception{
    return rikka.shizuku.RemoteShell.start(script);
  }
  private Result run(String script)throws Exception{
    // Redirect stderr into stdout so the read cannot deadlock on a full error pipe.
    Process p=proc("( "+script+" ) 2>&1");p.getOutputStream().close();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
    try(InputStream input=p.getInputStream()){while((n=input.read(b))!=-1){if(out.size()<12000)out.write(b,0,Math.min(n,12000-out.size()));}}
    return new Result(p.waitFor(),out.toString("UTF-8").trim());
  }
  private Result streamZip(File file)throws Exception{
    Process p=proc("umask 077; cat > "+sq(TMP)+" 2>/dev/null");
    try(InputStream in=new FileInputStream(file);OutputStream out=p.getOutputStream()){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();}
    int code=p.waitFor();if(code!=0)return new Result(code,"无法向 Shizuku 临时目录传送文件（退出码 "+code+"）");return new Result(0,"传输完成");
  }
}
