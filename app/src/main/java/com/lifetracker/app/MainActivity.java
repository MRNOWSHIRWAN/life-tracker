package com.lifetracker.app;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.content.Intent;
import android.net.Uri;
import android.webkit.*;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
public class MainActivity extends Activity {
 private WebView web; private ValueCallback<Uri[]> fileCallback; private String pendingExport;
 @Override public void onCreate(Bundle state){super.onCreate(state);
  getWindow().setStatusBarColor(0xff10151b);getWindow().setNavigationBarColor(0xff10151b);
  web=new WebView(this);setContentView(web);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);s.setAllowContentAccess(true);s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);
  web.addJavascriptInterface(new ExportBridge(),"NativeBackup");web.addJavascriptInterface(new PingBridge(),"NativePing");Pings.ensureChannel(this);
  web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !r.getUrl().toString().startsWith("file:///android_asset/");}});
  web.setWebChromeClient(new WebChromeClient(){
@Override public boolean onJsConfirm(WebView view,String url,String message,final JsResult result){new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("OK",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface dialog,int which){result.confirm();}}).setNegativeButton("Cancel",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface dialog,int which){result.cancel();}}).setOnCancelListener(new android.content.DialogInterface.OnCancelListener(){public void onCancel(android.content.DialogInterface dialog){result.cancel();}}).show();return true;}
@Override public boolean onJsAlert(WebView view,String url,String message,final JsResult result){new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("OK",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface dialog,int which){result.confirm();}}).setOnCancelListener(new android.content.DialogInterface.OnCancelListener(){public void onCancel(android.content.DialogInterface dialog){result.confirm();}}).show();return true;}
@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");try{startActivityForResult(i,11);return true;}catch(Exception e){fileCallback=null;cb.onReceiveValue(null);return false;}}});
  web.loadUrl("file:///android_asset/index.html");
 }

 @Override protected void onResume(){super.onResume();Pings.scheduleAll(this);}
 @Override public void onRequestPermissionsResult(int req,String[] p,int[] r){super.onRequestPermissionsResult(req,p,r);if(req==13)Pings.scheduleAll(this);}
 private void openNotifSettings(){Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());try{startActivity(i);}catch(Exception e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}}
 public class PingBridge {
  @JavascriptInterface public void sync(String json){Pings.saveAndSchedule(MainActivity.this,json);}
  @JavascriptInterface public String status(){return "{\"notifications\":"+Pings.notificationsOn(MainActivity.this)+",\"exact\":"+Pings.canExact(MainActivity.this)+"}";}
  @JavascriptInterface public void requestPermission(){runOnUiThread(new Runnable(){public void run(){
   if(!Pings.notificationsOn(MainActivity.this)){
    android.content.SharedPreferences sp=getSharedPreferences("pings",MODE_PRIVATE);
    boolean runtime=Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED;
    if(runtime&&!sp.getBoolean("asked",false)){sp.edit().putBoolean("asked",true).apply();requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},13);}else openNotifSettings();
   }else if(!Pings.canExact(MainActivity.this)&&Build.VERSION.SDK_INT>=31){
    try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
   }}});}
 }
 public class ExportBridge {@JavascriptInterface public void save(String data,String name){runOnUiThread(new Runnable(){public void run(){pendingExport=data;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,12);}});}}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
  if(request==11&&fileCallback!=null){fileCallback.onReceiveValue(result==RESULT_OK&&data!=null?new Uri[]{data.getData()}:null);fileCallback=null;}
  if(request==12&&result==RESULT_OK&&data!=null&&pendingExport!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(pendingExport.getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"Backup saved",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Backup could not be saved. Try again.",Toast.LENGTH_LONG).show();}}
  if(request==12)pendingExport=null;
 }
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
