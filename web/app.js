(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const STORAGE = {bookmarks:"nova-bookmarks-v1",history:"nova-history-v1",settings:"nova-settings-v1"};
  const defaults = {engine:"google",dark:true,reduceMotion:false};
  let settings = {...defaults,...read(STORAGE.settings,{})};
  let bookmarks = read(STORAGE.bookmarks,[]);
  let historyItems = read(STORAGE.history,[]);
  let tabs = [{id:1,title:"New tab",url:"",stack:[],index:-1}];
  let activeId = 1;
  let toastTimer = null;
  let currentLibrary = "bookmarks";

  function read(key,fallback){try{return JSON.parse(localStorage.getItem(key)) ?? fallback;}catch{return fallback;}}
  function save(key,value){try{localStorage.setItem(key,JSON.stringify(value));}catch{}}
  function activeTab(){return tabs.find(t=>t.id===activeId) || tabs[0];}
  function cleanTitle(url){try{return new URL(url).hostname.replace(/^www\./,"");}catch{return "New tab";}}
  function toast(message){const el=$("toast");el.textContent=message;el.classList.add("show");clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove("show"),2400);}
  function setStatus(message){$("pageStatus").textContent=message;$("footerStatus").textContent=message;}
  function showView(name){$("homeView").classList.toggle("hidden",name!=="home");$("browserView").classList.toggle("hidden",name!=="browser");$("libraryView").classList.toggle("hidden",name!=="library");$("settingsView").classList.toggle("hidden",name!=="settings");}
  function applySettings(){
    document.body.classList.toggle("reduce-motion",!!settings.reduceMotion);
    $("darkToggle").checked=!!settings.dark;$("motionToggle").checked=!!settings.reduceMotion;
    $("searchEngine").value=settings.engine;$("settingsSearchEngine").value=settings.engine;
    document.documentElement.style.colorScheme=settings.dark?"dark":"dark";
  }
  function normalizeInput(raw){
    const value=String(raw||"").trim();if(!value)return "";
    if(/^https?:\/\//i.test(value))return value;
    if(/^((localhost)|(\d{1,3}\.){3}\d{1,3})(:\d+)?([/].*)?$/i.test(value))return "http://"+value;
    if(value.includes(" ")||!value.includes("."))return searchUrl(value);
    return "https://"+value;
  }
  function searchUrl(query){
    const q=encodeURIComponent(query);
    if(settings.engine==="bing")return "https://www.bing.com/search?q="+q;
    if(settings.engine==="duckduckgo")return "https://duckduckgo.com/?q="+q;
    return "https://www.google.com/search?q="+q;
  }
  function isDirectVideo(url){return /\.(mp4|webm|ogv|ogg|m4v)(\?.*)?$/i.test(url);}
  function youtubeId(url){try{const u=new URL(url);if(u.hostname.includes("youtu.be"))return u.pathname.slice(1);if(u.hostname.includes("youtube.com")){if(u.pathname==="/watch")return u.searchParams.get("v");const m=u.pathname.match(/\/(embed|shorts|live)\/([^/?]+)/);if(m)return m[2];}}catch{}return null;}
  function setAddress(url){$("addressInput").value=url||"";}
  function renderTabs(){
    $("tabStrip").innerHTML="";
    tabs.forEach(tab=>{
      const el=document.createElement("div");el.className="tab"+(tab.id===activeId?" active":"");el.setAttribute("role","tab");el.setAttribute("aria-selected",tab.id===activeId?"true":"false");
      const fav=document.createElement("span");fav.className="tab-favicon";fav.textContent=tab.url?cleanTitle(tab.url).slice(0,1).toUpperCase():"N";
      const title=document.createElement("button");title.className="tab-title";title.textContent=tab.title||"New tab";title.addEventListener("click",()=>switchTab(tab.id));
      const close=document.createElement("button");close.className="tab-close";close.setAttribute("aria-label","Close tab");close.textContent="×";close.addEventListener("click",e=>{e.stopPropagation();closeTab(tab.id);});
      el.append(fav,title,close);$("tabStrip").appendChild(el);
    });
  }
  function switchTab(id){activeId=id;const tab=activeTab();renderTabs();setAddress(tab.url);if(tab.url)renderPage(tab.url,false);else showView("home");}
  function newTab(){
    if(tabs.length>=3){toast("NOVA keeps up to 3 tabs open on this device.");return;}
    const id=Math.max(...tabs.map(t=>t.id))+1;tabs.push({id,title:"New tab",url:"",stack:[],index:-1});activeId=id;renderTabs();setAddress("");showView("home");$("heroSearchInput").focus();
  }
  function closeTab(id){
    if(tabs.length===1){tabs[0]={id:tabs[0].id,title:"New tab",url:"",stack:[],index:-1};switchTab(tabs[0].id);return;}
    const i=tabs.findIndex(t=>t.id===id);tabs=tabs.filter(t=>t.id!==id);if(activeId===id)activeId=tabs[Math.max(0,i-1)].id;switchTab(activeId);
  }
  function navigate(raw,push=true){
    const url=normalizeInput(raw);if(!url)return;
    const tab=activeTab();
    if(push){tab.stack=tab.stack.slice(0,tab.index+1);tab.stack.push(url);tab.index=tab.stack.length-1;}
    tab.url=url;tab.title=cleanTitle(url);setAddress(url);renderTabs();renderPage(url,true);
    historyItems=[{url,title:tab.title,time:Date.now()},...historyItems.filter(x=>x.url!==url)].slice(0,100);save(STORAGE.history,historyItems);
  }
  function renderPage(url,updateStatus=true){
    showView("browser");$("pageFrame").classList.remove("hidden");$("videoStage").classList.add("hidden");$("embedHint").classList.remove("hidden");
    $("pageFrame").src="about:blank";$("videoPlayer").pause();$("videoPlayer").removeAttribute("src");$("videoPlayer").load();
    const yt=youtubeId(url);
    if(isDirectVideo(url)){
      $("pageFrame").classList.add("hidden");$("videoStage").classList.remove("hidden");$("embedHint").classList.add("hidden");
      $("videoPlayer").src=url;$("videoTitle").textContent=cleanTitle(url);setStatus("Video ready");
    }else if(yt){
      $("pageFrame").src="https://www.youtube-nocookie.com/embed/"+encodeURIComponent(yt)+"?autoplay=0&rel=0";$("embedHint").classList.add("hidden");setStatus("YouTube player");
    }else{
      $("pageFrame").src=url;setStatus("Loading "+cleanTitle(url));
      if(updateStatus){setTimeout(()=>{if(activeTab().url===url)setStatus(cleanTitle(url)+" · embedded view");},1200);}
    }
    $("pageFrame").style.width="100%";
  }
  function goBack(){
    const t=activeTab();if(t.index>0){t.index--;t.url=t.stack[t.index];t.title=cleanTitle(t.url);setAddress(t.url);renderTabs();renderPage(t.url);}else{showView("home");setStatus("NOVA is ready");}
  }
  function goForward(){const t=activeTab();if(t.index<t.stack.length-1){t.index++;t.url=t.stack[t.index];t.title=cleanTitle(t.url);setAddress(t.url);renderTabs();renderPage(t.url);}}
  function openFullSite(){const url=activeTab().url;if(!url)return;const win=window.open(url,"_blank","noopener");if(!win){window.location.assign(url);}else{toast("Opened in a new browser tab.");}}
  function addBookmark(url=activeTab().url){
    if(!url){toast("Open a page before bookmarking it.");return;}
    if(bookmarks.some(x=>x.url===url)){toast("Already in your bookmarks.");return;}
    bookmarks.unshift({url,title:cleanTitle(url)});bookmarks=bookmarks.slice(0,100);save(STORAGE.bookmarks,bookmarks);toast("Saved to bookmarks.");
  }
  function openLibrary(kind){
    currentLibrary=kind;const items=kind==="history"?historyItems:bookmarks;
    $("libraryKicker").textContent=kind==="history"?"RECENTLY VISITED":"YOUR SAVED PLACES";
    $("libraryTitle").textContent=kind==="history"?"History":"Bookmarks";
    $("librarySubtitle").textContent=kind==="history"?"Your recent destinations on this device.":"Your saved places, ready when you are.";
    const list=$("libraryList");list.innerHTML="";
    if(!items.length){const empty=document.createElement("div");empty.className="empty-state";empty.textContent=kind==="history"?"No browsing history yet.":"No bookmarks yet. Save a website using the ☆ button.";list.appendChild(empty);}
    items.forEach((item,index)=>{
      const row=document.createElement("div");row.className="library-item";
      const icon=document.createElement("span");icon.className="library-favicon";icon.textContent=(item.title||cleanTitle(item.url)).slice(0,1).toUpperCase();
      const main=document.createElement("div");main.className="library-item-main";const strong=document.createElement("strong");strong.textContent=item.title||cleanTitle(item.url);const small=document.createElement("small");small.textContent=item.url+(item.time?" · "+new Date(item.time).toLocaleString():"");main.append(strong,small);
      const open=document.createElement("button");open.textContent="Open";open.addEventListener("click",()=>{navigate(item.url);});
      row.append(icon,main,open);
      if(kind==="bookmarks"){const remove=document.createElement("button");remove.textContent="Remove";remove.addEventListener("click",()=>{bookmarks.splice(index,1);save(STORAGE.bookmarks,bookmarks);openLibrary("bookmarks");});row.append(remove);}
      list.appendChild(row);
    });
    showView("library");
  }
  function openSettings(){applySettings();showView("settings");}
  function fullScreen(){
    const target=$("viewerStage");if(document.fullscreenElement){document.exitFullscreen?.();}else{target.requestFullscreen?.().catch(()=>toast("Fullscreen is not available in this browser."));}
  }
  function tickClock(){const d=new Date();$("clock").textContent=d.toLocaleTimeString([],{hour:"2-digit",minute:"2-digit"});}
  $("addressForm").addEventListener("submit",e=>{e.preventDefault();navigate($("addressInput").value);});
  $("heroSearchForm").addEventListener("submit",e=>{e.preventDefault();navigate($("heroSearchInput").value);});
  $("searchEngine").addEventListener("change",e=>{settings.engine=e.target.value;save(STORAGE.settings,settings);$("settingsSearchEngine").value=settings.engine;});
  $("settingsSearchEngine").addEventListener("change",e=>{settings.engine=e.target.value;save(STORAGE.settings,settings);$("searchEngine").value=settings.engine;});
  $("shortcutGrid").addEventListener("click",e=>{const card=e.target.closest("[data-url]");if(card)navigate(card.dataset.url);});
  $("manageShortcutsBtn").addEventListener("click",()=>toast("Choose any shortcut to open its website."));
  $("homeBtn").addEventListener("click",()=>{setAddress("");showView("home");setStatus("NOVA is ready");});
  $("homeControlBtn").addEventListener("click",()=>{setAddress("");showView("home");});
  $("backBtn").addEventListener("click",goBack);$("forwardBtn").addEventListener("click",goForward);
  $("reloadBtn").addEventListener("click",()=>{const t=activeTab();if(t.url)renderPage(t.url);else toast("Nothing to reload.");});
  $("newTabBtn").addEventListener("click",newTab);$("bookmarkCurrentBtn").addEventListener("click",()=>addBookmark());
  $("openExternalBtn").addEventListener("click",openFullSite);$("hintOpenExternalBtn").addEventListener("click",openFullSite);
  $("fullscreenBtn").addEventListener("click",fullScreen);
  $("copyLinkBtn").addEventListener("click",async()=>{const url=activeTab().url;if(!url){toast("Open a website first.");return;}try{await navigator.clipboard.writeText(url);toast("Link copied.");}catch{setAddress(url);$("addressInput").focus();$("addressInput").select();toast("Select and copy the address above.");}});
  $("bookmarksLink").addEventListener("click",()=>openLibrary("bookmarks"));$("historyLink").addEventListener("click",()=>openLibrary("history"));
  $("videoLink").addEventListener("click",()=>{$("addressInput").focus();toast("Paste a direct MP4, WebM or Ogg video address.");});
  $("libraryBackBtn").addEventListener("click",()=>{const t=activeTab();t.url?renderPage(t.url,false):showView("home");});
  $("settingsBtn").addEventListener("click",openSettings);$("settingsBackBtn").addEventListener("click",()=>{activeTab().url?renderPage(activeTab().url,false):showView("home");});
  $("darkToggle").addEventListener("change",e=>{settings.dark=e.target.checked;save(STORAGE.settings,settings);toast("Appearance preference saved.");});
  $("motionToggle").addEventListener("change",e=>{settings.reduceMotion=e.target.checked;save(STORAGE.settings,settings);applySettings();});
  $("clearDataBtn").addEventListener("click",()=>{if(confirm("Clear all local bookmarks and browsing history?")){bookmarks=[];historyItems=[];save(STORAGE.bookmarks,bookmarks);save(STORAGE.history,historyItems);toast("Local browsing data cleared.");}});
  document.addEventListener("keydown",e=>{
    const target=e.target;const typing=target&&(target.tagName==="INPUT"||target.tagName==="TEXTAREA"||target.isContentEditable);
    if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==="l"){e.preventDefault();$("addressInput").focus();$("addressInput").select();}
    else if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==="t"){e.preventDefault();newTab();}
    else if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==="w"){e.preventDefault();closeTab(activeId);}
    else if(e.key==="Escape"&&!typing){if(document.fullscreenElement)document.exitFullscreen?.();else if(!$("settingsView").classList.contains("hidden")||!$("libraryView").classList.contains("hidden")){activeTab().url?renderPage(activeTab().url,false):showView("home");}}
    else if((e.key==="Backspace"||e.key==="BrowserBack")&&!typing){e.preventDefault();goBack();}
    else if(e.key==="Enter"&&target===$("addressInput")){e.preventDefault();navigate(target.value);}
  });
  if("serviceWorker" in navigator && location.protocol!=="file:"){window.addEventListener("load",()=>navigator.serviceWorker.register("./sw.js").catch(()=>{}));}
  applySettings();renderTabs();showView("home");tickClock();setInterval(tickClock,30000);
})();