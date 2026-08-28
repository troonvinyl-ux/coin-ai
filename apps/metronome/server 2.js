const http = require('http');
const https = require('https');
const {URL} = require('url');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;
const ROOT = __dirname;

function requestText(target){
  return new Promise((resolve,reject)=>{
    const u=new URL(target);
    const lib=u.protocol==='https:'?https:http;
    const req=lib.get(u,{
      headers:{
        'User-Agent':'Mozilla/5.0 (compatible; MetronomeVideoGallery/1.0)',
        'Accept':'text/html,application/xhtml+xml'
      }
    },res=>{
      if(res.statusCode>=300 && res.statusCode<400 && res.headers.location){
        const next=new URL(res.headers.location,target).toString();
        res.resume(); return requestText(next).then(resolve,reject);
      }
      let data='';
      res.setEncoding('utf8');
      res.on('data',c=>{data+=c;if(data.length>8_000_000)res.destroy(new Error('Page too large'))});
      res.on('end',()=>resolve({status:res.statusCode||0,body:data,finalUrl:target}));
    });
    req.setTimeout(15000,()=>req.destroy(new Error('Request timed out')));
    req.on('error',reject);
  });
}
function abs(base,value){
  try{return new URL(value,base).toString()}catch{return null}
}
function attr(tag,name){
  const m=tag.match(new RegExp(name+'\\\\s*=\\\\s*["\\\\\\']([^"\\\\\\']+)["\\\\\\']','i'));
  return m?m[1]:null;
}
function clean(s){return (s||'').replace(/<[^>]*>/g,' ').replace(/\\s+/g,' ').trim()}

function extract(html,base){
  const out=[], seen=new Set();
  const add=(video,thumb,title,embed)=>{
    if(!video && !embed)return;
    const key=video||embed;if(seen.has(key))return;seen.add(key);
    out.push({video:video?abs(base,video):null,thumb:thumb?abs(base,thumb):null,title:clean(title)||'Video',embed:embed?abs(base,embed):null});
  };

  for(const m of html.matchAll(/<video\\b[^>]*>([\\s\\S]*?)<\\/video>/gi)){
    const block=m[0], inner=m[1];
    const poster=attr(block,'poster');
    const sources=[...inner.matchAll(/<source\\b[^>]*>/gi)].map(x=>attr(x[0],'src')).filter(Boolean);
    add(sources[0],poster,'Video');
  }
  for(const m of html.matchAll(/<(?:meta|link)\\b[^>]*>/gi)){
    const tag=m[0], prop=(attr(tag,'property')||attr(tag,'name')||'').toLowerCase();
    const content=attr(tag,'content')||attr(tag,'href');
    if(!content)continue;
    if(prop==='og:video'||prop==='og:video:url'||prop==='twitter:player:stream'){
      add(content,null,'Video');
    }
  }
  for(const m of html.matchAll(/(?:href|src|data-src|data-video|data-video-url)\\s*=\\s*["']([^"']+)["']/gi)){
    const v=m[1]; if(/\\.(mp4|webm|ogg)(?:[?#]|$)/i.test(v))add(v,null,'Video');
  }
  for(const m of html.matchAll(/<iframe\\b[^>]*>/gi)){
    const src=attr(m[0],'src'); if(src)add(null,null,'Embedded video',src);
  }
  return out.slice(0,200);
}

function staticFile(req,res){
  let p=new URL(req.url,'http://localhost').pathname;
  if(p==='/'||p==='/index.html')p='/index.html';
  if(p==='/manifest.webmanifest'){res.writeHead(200,{'Content-Type':'application/manifest+json'});return res.end(fs.readFileSync(path.join(ROOT,p)));}
  if(p==='/sw.js'){res.writeHead(200,{'Content-Type':'application/javascript'});return res.end(fs.readFileSync(path.join(ROOT,p)));}
  const file=path.join(ROOT,p.replace(/^\\//,''));
  if(!file.startsWith(ROOT)||!fs.existsSync(file)){res.writeHead(404);return res.end('Not found')}
  const ext=path.extname(file),types={'.html':'text/html; charset=utf-8','.js':'application/javascript','.css':'text/css'};
  res.writeHead(200,{'Content-Type':types[ext]||'application/octet-stream'});res.end(fs.readFileSync(file));
}

const server=http.createServer(async(req,res)=>{
  try{
    if(req.url.startsWith('/api/scrape')){
      const u=new URL(req.url,'http://localhost').searchParams.get('url');
      if(!u || !/^https?:\\/\\//i.test(u))throw new Error('Invalid URL');
      const result=await requestText(u);
      if(result.status>=400)throw new Error('Source returned HTTP '+result.status);
      const items=extract(result.body,result.finalUrl);
      res.writeHead(200,{'Content-Type':'application/json; charset=utf-8','Access-Control-Allow-Origin':'*'});
      return res.end(JSON.stringify({source:result.finalUrl,items}));
    }
    staticFile(req,res);
  }catch(e){
    res.writeHead(400,{'Content-Type':'application/json; charset=utf-8'});
    res.end(JSON.stringify({error:e.message}));
  }
});
server.listen(PORT,()=>console.log('Metronome Video Gallery listening on '+PORT));
