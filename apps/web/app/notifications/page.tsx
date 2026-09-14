"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppShell } from "../components/AppShell";
import { browserAccessToken, listNotifications, markAllNotificationsRead, markNotificationRead, type NotificationItem } from "../lib/api";

export default function NotificationsPage(){
 const[token,setToken]=useState<string|null>(null),[items,setItems]=useState<NotificationItem[]>([]),[unread,setUnread]=useState(0),[loading,setLoading]=useState(true),[error,setError]=useState("");
 async function load(){const access=browserAccessToken();setToken(access);if(!access){setLoading(false);return;}setLoading(true);setError("");try{const result=await listNotifications(access);setItems(result.notifications);setUnread(result.unreadCount);}catch(e){setError(e instanceof Error?e.message:"Bildirimler yüklenemedi.");}finally{setLoading(false);}}
 useEffect(()=>{void load();},[]);
 async function read(id:string){if(!token)return;try{await markNotificationRead(token,id);await load();}catch(e){setError(e instanceof Error?e.message:"Bildirim güncellenemedi.");}}
 async function readAll(){if(!token)return;try{await markAllNotificationsRead(token);await load();}catch(e){setError(e instanceof Error?e.message:"Bildirimler güncellenemedi.");}}
 return <AppShell title="Bildirimler" kicker={`${unread} OKUNMAMIŞ`}>{!token&&!loading?<section className="yl-card yl-empty"><strong>Bildirimler için giriş yap</strong><p>Hesabına ait bildirimler yalnızca oturum açıldığında gösterilir.</p><Link className="yl-btn" href="/profile" style={{marginTop:14}}>Giriş yap</Link></section>:<><div style={{display:"flex",justifyContent:"flex-end",marginBottom:12}}><button className="yl-btn-secondary" onClick={()=>void readAll()} disabled={!unread}>Tümünü okundu işaretle</button></div>{error&&<p className="yl-error">{error}</p>}{loading?<div className="yl-card yl-loading">Bildirimler yükleniyor…</div>:items.length===0?<div className="yl-card yl-empty"><strong>Bildirim yok</strong><p>Yeni bildirimler burada görünecek.</p></div>:<div className="yl-stack">{items.map(item=><article className={`yl-card ${item.readAt?"":"yl-status-ok"}`} key={item.id}><div className="yl-order-top"><strong>{item.title}</strong><span className="yl-badge neutral">{item.readAt?"Okundu":"Yeni"}</span></div><p className="yl-muted">{item.body}</p><small>{item.createdAt.slice(0,16).replace("T"," ")}</small>{!item.readAt&&<button className="yl-btn-secondary" onClick={()=>void read(item.id)} style={{marginTop:10}}>Okundu işaretle</button>}</article>)}</div>}</>}</AppShell>;
}
