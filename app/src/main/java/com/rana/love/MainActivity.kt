package com.rana.love

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val bg = Color.rgb(17,11,24)
    private val card = Color.rgb(35,25,43)
    private val pink = Color.rgb(255,92,158)
    private val white = Color.rgb(246,239,249)
    private val muted = Color.rgb(177,158,184)
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private val prefs by lazy { getSharedPreferences("rana_love", MODE_PRIVATE) }
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var socket: WebSocket? = null
    private var page = "home"
    private var serverUrl = "https://theories-saved-dave-wto.trycloudflare.com"
    private var token = ""
    private var role = "me"
    private val messages = mutableListOf<Pair<String,String>>()
    private val memories = mutableListOf<String>()
    private val pickedGallery = ArrayList<String>()
    private var audioUri: Uri? = null
    private var player: android.media.MediaPlayer? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor=bg; window.navigationBarColor=bg
        serverUrl=prefs.getString("server_url",serverUrl) ?: serverUrl
        token=prefs.getString("token","") ?: ""
        role=prefs.getString("role","me") ?: "me"
        loadData(); buildShell(); showHome()
        if(token.isNotBlank()) connectSocket()
    }

    private fun loadData(){
        prefs.getStringSet("memories",emptySet())?.let{memories.addAll(it)}
        prefs.getString("gallery","")?.split("|")?.filter{it.isNotBlank()}?.let{pickedGallery.addAll(it)}
        prefs.getString("messages","")?.split("|||")?.forEach{val a=it.split("::",limit=2);if(a.size==2)messages.add(a[0] to a[1])}
        prefs.getString("audio","")?.let{if(it.isNotBlank())audioUri=Uri.parse(it)}
    }
    private fun saveLocal(){prefs.edit().putStringSet("memories",memories.toSet()).putString("gallery",pickedGallery.joinToString("|")).putString("messages",messages.joinToString("|||"){"${it.first}::${it.second}"}).apply()}
    private fun buildShell(){root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)};val sc=ScrollView(this).apply{layoutParams=LinearLayout.LayoutParams(-1,0,1f)};content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,18,24,24)};sc.addView(content);root.addView(sc);root.addView(bottomNav());setContentView(root)}
    private fun bottomNav():View{val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(8,8,8,8);setBackgroundColor(Color.rgb(28,18,36))};listOf("♥\nالبيت","♡\nذكريات","▣\nالمعرض","●\nالمحادثة","♫\nأغانينا").forEachIndexed{i,s->bar.addView(Button(this).apply{text=s;textSize=11f;setTextColor(if((page=="home"&&i==0)||(page=="memory"&&i==1)||(page=="gallery"&&i==2)||(page=="chat"&&i==3)||(page=="music"&&i==4))pink else muted);setBackgroundColor(Color.TRANSPARENT);setOnClickListener{when(i){0->showHome();1->showMemories();2->showGallery();3->showChat();4->showMusic()}}},LinearLayout.LayoutParams(0,76,1f))};return bar}
    private fun refreshNav(){root.removeViewAt(root.childCount-1);root.addView(bottomNav())}
    private fun reset(title:String,sub:String?=null){content.removeAllViews();content.addView(TextView(this).apply{text=title;textSize=28f;setTextColor(white);typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.RIGHT;setPadding(0,8,0,8)});sub?.let{content.addView(TextView(this).apply{text=it;textSize=15f;setTextColor(muted);gravity=Gravity.RIGHT;setPadding(0,0,0,18)})}}
    private fun card(t:String,click:(()->Unit)?=null)=TextView(this).apply{text=t;textSize=17f;setTextColor(white);gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;setPadding(22,18,22,18);setBackgroundColor(card);layoutParams=LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT).also{it.setMargins(0,8,0,8)};click?.let{setOnClickListener{it()}}}
    private fun showHome(){page="home";reset("رنا ❤️","من 5 مارس 2026، بدأت حكايتنا.");content.addView(card(if(token.isBlank())"🔐  ربط المحادثة\n\nاضغطي هنا لإدخال كود الاقتران وربط الجهازين." else "🟢  المحادثة متصلة\n\nالحساب: ${if(role=="me")"أنا" else "رنا"}\nالخادم: متصل" ){showPair()});content.addView(card("💗  نبضة حب\n\nاضغطي لإرسال نبضة قلب",{if(token.isBlank())showPair()else send("❤️") }));content.addView(card("📌 الحالة\n\nالذكريات: ${memories.size} • الصور: ${pickedGallery.size} • الرسائل: ${messages.size}",null));refreshNav()}
    private fun showPair(){page="home";reset("🔐 ربط رنا ❤️","استخدمي نفس كود الاقتران على الجهازين.")
        val url=EditText(this).apply{setText(serverUrl);hint="رابط السيرفر";setTextColor(white);setHintTextColor(muted);textSize=14f;setPadding(18,18,18,18);setBackgroundColor(card)};content.addView(url)
        val secret=EditText(this).apply{hint="كود الاقتران";setTextColor(white);setHintTextColor(muted);textSize=16f;inputType=0x00000081;setPadding(18,18,18,18);setBackgroundColor(card)};content.addView(secret)
        val roleSpinner=Spinner(this);roleSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("أنا","رنا"));roleSpinner.setSelection(if(role=="rana")1 else 0);content.addView(roleSpinner)
        content.addView(card("🔗 اتصال واختبار", {pair(url.text.toString().trim().trimEnd('/'),secret.text.toString(),if(roleSpinner.selectedItemPosition==1)"rana" else "me")}));refreshNav()}
    private fun pair(url:String,secret:String,newRole:String){if(url.isBlank()||secret.length<12){toast("الرابط والكود مطلوبان، والكود 12 حرفاً على الأقل");return};serverUrl=url;role=newRole;val body=RequestBody.create("application/json".toMediaTypeOrNull(),JSONObject().put("secret",secret).toString());client.newCall(Request.Builder().url("$url/api/pair").post(body).build()).enqueue(object:Callback{override fun onFailure(c:Call,e:java.io.IOException){runOnUiThread{toast("تعذر الاتصال بالسيرفر: ${e.message}")}};override fun onResponse(c:Call,r:Response){val txt=r.body?.string().orEmpty();if(!r.isSuccessful){runOnUiThread{toast("كود الاقتران غير صحيح")};return};try{val o=JSONObject(txt);token=o.getString("token");prefs.edit().putString("server_url",serverUrl).putString("token",token).putString("role",role).apply();runOnUiThread{toast("تم الربط ❤️");loadHistory();connectSocket();showHome()}}catch(e:Exception){runOnUiThread{toast("رد غير صالح من السيرفر")}}}})}
    private fun loadHistory(){client.newCall(Request.Builder().url("$serverUrl/api/messages?room=ranaa&token=$token").get().build()).enqueue(object:Callback{override fun onFailure(c:Call,e:java.io.IOException){};override fun onResponse(c:Call,r:Response){if(!r.isSuccessful)return;try{val a=JSONObject(r.body?.string().orEmpty()).getJSONArray("messages");messages.clear();for(i in 0 until a.length()){val o=a.getJSONObject(i);messages.add(o.getString("sender") to o.getString("body"))};saveLocal();runOnUiThread{if(page=="chat")showChat()}}catch(_:Exception){}}})}
    private fun connectSocket(){socket?.close(1000,"reconnect");val wsUrl=serverUrl.replaceFirst("^https".toRegex(),"wss").replaceFirst("^http".toRegex(),"ws")+"/ws?room=ranaa&token=$token";socket=client.newWebSocket(Request.Builder().url(wsUrl).build(),object:WebSocketListener(){override fun onOpen(w:WebSocket,r:Response){runOnUiThread{if(page=="home")showHome()}};override fun onMessage(w:WebSocket,text:String){try{val o=JSONObject(text);if(o.optString("type")=="message"){val m=o.getJSONObject("message");messages.add(m.getString("sender") to m.getString("body"));saveLocal();runOnUiThread{if(page=="chat")showChat()}}}catch(_:Exception){}};override fun onFailure(w:WebSocket,t:Throwable,r:Response?){runOnUiThread{if(page=="home")showHome()}}})}
    private fun send(body:String){val ws=socket;if(ws==null){toast("المحادثة غير متصلة");return};ws.send(JSONObject().put("sender",role).put("body",body).toString())}
    private fun showChat(){page="chat";reset("💬 محادثتنا",if(token.isBlank())"اربط الجهازين أولاً." else "محادثة حقيقية بين الجهازين.");if(token.isBlank()){content.addView(card("🔐 ربط المحادثة",{showPair()}));refreshNav();return};if(messages.isEmpty())content.addView(card("لا توجد رسائل بعد ❤️",null));messages.forEach{(who,msg)->content.addView(card("${if(who=="me")"أنا" else "رنا"} • $msg",null))};val input=EditText(this).apply{hint="اكتبي رسالة...";setTextColor(white);setHintTextColor(muted);gravity=Gravity.RIGHT;setBackgroundColor(card)};content.addView(input);content.addView(card("إرسال 💌"){val s=input.text.toString().trim();if(s.isNotEmpty()){send(s);input.text.clear()}});refreshNav()}
    private fun showMemories(){page="memory";reset("💕 ذكرياتنا");val input=EditText(this).apply{hint="مثلاً: أول مرة التقينا...";setTextColor(white);setHintTextColor(muted);setBackgroundColor(card);gravity=Gravity.RIGHT};content.addView(input);content.addView(card("➕ حفظ الذكرى"){val s=input.text.toString().trim();if(s.isNotEmpty()){memories.add(s);saveLocal();showMemories()}});memories.asReversed().forEach{content.addView(card("💗  $it"))};refreshNav()}
    private fun showGallery(){page="gallery";reset("🖼️ معرضنا");content.addView(card("＋ إضافة صور من الهاتف"){pickImages()});if(pickedGallery.isEmpty())content.addView(card("لا توجد صور بعد.",null))else pickedGallery.forEachIndexed{i,u->content.addView(card("📷 صورة ${i+1}\n$u"))};refreshNav()}
    private fun pickImages(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);addCategory(Intent.CATEGORY_OPENABLE)},100)}
    private fun showMusic(){page="music";reset("♫ أغانينا","بينا معاد");content.addView(card("🎵 إضافة أغنية من الهاتف"){pickAudio()});content.addView(card(if(audioUri==null)"لا توجد أغنية مختارة." else "🎶 الأغنية المختارة\nاضغطي للتشغيل/الإيقاف"){audioUri?.let{togglePlayer(it)}});refreshNav()}
    private fun pickAudio(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="audio/*";addCategory(Intent.CATEGORY_OPENABLE)},101)}
    private fun togglePlayer(uri:Uri){if(player?.isPlaying==true){player?.pause();toast("تم الإيقاف");return};player?.release();player=android.media.MediaPlayer.create(this,uri);player?.start();toast("تشغيل 🎵")}
    override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null)return;if(req==100){val c=data.clipData;if(c!=null)for(i in 0 until c.itemCount)pickedGallery.add(c.getItemAt(i).uri.toString()) else data.data?.let{pickedGallery.add(it.toString())};saveLocal();showGallery()}else if(req==101){data.data?.let{audioUri=it;prefs.edit().putString("audio",it.toString()).apply();showMusic()}}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    override fun onDestroy(){socket?.close(1000,"bye");player?.release();saveLocal();super.onDestroy()}
}
