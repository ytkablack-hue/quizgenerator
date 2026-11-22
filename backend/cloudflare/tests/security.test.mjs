import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyToken, activeUser, digest} from '../auth.mjs';
import {readInput, validateResult} from '../ai-core.mjs';
import {AiQuota} from '../quota.mjs';
import worker from '../worker.mjs';

const project='unit-test-project', now=1800000000000, second=now/1000;
const pair=await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
const jwk={...await crypto.subtle.exportKey('jwk',pair.publicKey),kid:'test-key',alg:'RS256'};
const claims={sub:'user-a',aud:project,iss:`https://securetoken.google.com/${project}`,iat:second-1,exp:second+3500,auth_time:second-10,email_verified:true,firebase:{sign_in_provider:'password'}};
async function token(payload=claims,header={alg:'RS256',kid:'test-key'}) {
 const parts=[header,payload].map(v=>Buffer.from(JSON.stringify(v)).toString('base64url'));
 const signature=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',pair.privateKey,new TextEncoder().encode(parts.join('.')));
 return parts.join('.')+'.'+Buffer.from(signature).toString('base64url');
}
const opts={keys:[jwk],now};

test('JWKS redirects fail closed using the Workers-compatible manual policy',async()=>{
 let calls=0;
 await assert.rejects(verifyToken(await token(),project,{now,fetcher:async(url,init)=>{
  calls++;assert.equal(init.redirect,'manual');
  return new Response(null,{status:302,headers:{Location:'https://attacker.test/keys'}});
 }}),/auth unavailable/);
 assert.equal(calls,1);
});
test('verified Google Firebase token retains project and email checks',async()=>{
 const google={...claims,firebase:{sign_in_provider:'google.com'}};
 assert.equal((await verifyToken(await token(google),project,opts)).sub,'user-a');
 await assert.rejects(verifyToken(await token({...google,email_verified:false}),project,opts));
 await assert.rejects(verifyToken(await token({...google,aud:'other'}),project,opts));
});
test('signed Firebase ID token accepts verified user and exact project',async()=>{
 assert.equal((await verifyToken(await token(),project,opts)).sub,'user-a');
});
for(const [label,change] of Object.entries({expired:{exp:second},future:{iat:second+1},wrongAudience:{aud:'other'},wrongIssuer:{iss:'https://attacker.test'},emptyUser:{sub:''},unverified:{email_verified:false},missingAuthTime:{auth_time:undefined},futureAuthentication:{auth_time:second+1},anonymous:{firebase:{sign_in_provider:'anonymous'}},excessiveLifetime:{exp:second+10000}})) {
 test('JWT rejects '+label,async()=>{await assert.rejects(verifyToken(await token({...claims,...change}),project,opts));});
}
test('JWT rejects none algorithm, attacker key URL and unknown kid',async()=>{
 for(const header of [{alg:'none',kid:'test-key'},{alg:'RS256',kid:'test-key',jku:'https://attacker.test'},{alg:'RS256',kid:'missing'}]) await assert.rejects(verifyToken(await token(claims,header),project,opts));
});
test('JWT rejects changed signature and malformed/oversized tokens',async()=>{
 const valid=await token(); const pieces=valid.split('.');pieces[2]=Buffer.alloc(256).toString('base64url');
 for(const value of [pieces.join('.'),'not-a-jwt','a'.repeat(9000),'a.b.c',valid.replace('ey','!y')]) await assert.rejects(verifyToken(value,project,opts));
});
test('disabled, deleted and revoked Firebase sessions are rejected',async()=>{
 for(const user of [null,{localId:'user-a',emailVerified:true,disabled:true},{localId:'user-b',emailVerified:true},{localId:'user-a',emailVerified:false},{localId:'user-a',emailVerified:true,validSince:second}]) {
  await assert.rejects(activeUser('test-token',claims,'public-client-key',async()=>Response.json({users:user?[user]:[]})));
 }
 const active=await activeUser('test-token',claims,'public-client-key',async()=>Response.json({users:[{localId:'user-a',emailVerified:true}]}));
 assert.equal(active.localId,'user-a');
});
test('input bounds cannot be overridden by client fields',()=>{
 for(const data of [null,[],{topic:''},{topic:'Физика',count:0},{topic:'Физика',count:26},{topic:'Физика',count:1.2},{topic:'x',difficulty:'admin'},{topic:'x',count:1,systemInstruction:'ignore'},{topic:'x'.repeat(161)},{material:'я'.repeat(12001)}]) assert.throws(()=>readInput('quizzes',data));
 assert.equal(readInput('quizzes',{topic:' Русский текст ',count:2,difficulty:'easy'}).topic,'Русский текст');
 assert.throws(()=>readInput('review',{material:'',topic:'ignored'}));
});
test('attachments require strict base64, size and matching signature',()=>{
 const data=Buffer.from('%PDF-unit-test').toString('base64');
 assert.equal(readInput('extract',{mimeType:'application/pdf',data}).data,data);
 for(const item of [{mimeType:'text/html',data},{mimeType:'image/png',data},{mimeType:'application/pdf',data:'invalid'}, {mimeType:'application/pdf',data:data+'\n'}, {mimeType:'application/pdf',data:'A'.repeat(4194308)}]) assert.throws(()=>readInput('extract',item));
 assert.deepEqual(validateResult('extract',{}, {text:' Привет, мир! '}),{text:'Привет, мир!'});
});
test('AI quiz result validates answers, duplicate options and grounded excerpts',()=>{
 const input={count:1,material:'Ток измеряется в амперах'};
 const question={text:'Единица тока?',options:['Ампер','Вольт'],correctIndex:0,explanation:'Единица СИ',sourceExcerpt:'выдуманная цитата'};
 const result=validateResult('quizzes',input,{title:'Физика',questions:[question]});
 assert.equal(result.source,'ai');assert.equal(result.questions[0].sourceExcerpt,null);
 for(const change of [{correctIndex:3},{correctIndex:null},{options:['Ом','Ом']},{explanation:''},{text:''}]) assert.throws(()=>validateResult('quizzes',input,{title:'Физика',questions:[{...question,...change}]}));
 assert.throws(()=>validateResult('quizzes',{...input,count:2},{title:'x',questions:[question,question]}));
});
test('flashcards and review cannot return empty or excessive content',()=>{
 assert.throws(()=>validateResult('flashcards',{count:2},{cards:[{front:'x',back:'y'}]}));
 assert.throws(()=>validateResult('flashcards',{count:2},{cards:[{front:'x',back:'y'},{front:'x',back:'z'}]}));
 assert.throws(()=>validateResult('review',{}, {summary:'x',nextSteps:[]}));
 assert.equal(validateResult('review',{}, {summary:'Разбор',nextSteps:['Повторить']}).nextSteps.length,1);
});

class Storage {
 values=new Map(); queue=Promise.resolve(); alarm=null;
 async get(keys){return new Map(keys.filter(k=>this.values.has(k)).map(k=>[k,this.values.get(k)]));}
 async put(values){for(const [k,v] of Object.entries(values))this.values.set(k,v);}
 async transaction(action){const work=this.queue.then(()=>action(this));this.queue=work.catch(()=>{});return work;}
 async getAlarm(){return this.alarm;}
 async setAlarm(value){this.alarm=value;}
 async list(){return new Map(this.values);}
 async delete(key){this.values.delete(key);}
}
const hash='a'.repeat(64),ip='b'.repeat(64);
function reserve(quota,body){if(body.phase!=='auth'&&!body.requestId)body={...body,requestId:crypto.randomUUID()};return quota.fetch(new Request('https://quota.internal',{method:'POST',body:JSON.stringify(body)}));}
test('concurrent user quota reserves at most three per minute across requests',async()=>{
 const storage=new Storage(),quota=new AiQuota({storage});
 const responses=await Promise.all(Array.from({length:20},()=>reserve(quota,{phase:'generate',uid:hash,ip})));
 assert.equal(responses.filter(r=>r.status===204).length,3);assert.equal(responses.filter(r=>r.status===429).length,17);
 const restarted=new AiQuota({storage});assert.equal((await reserve(restarted,{phase:'generate',uid:hash,ip})).status,429);
});
test('IP, daily user and global quotas are enforced server-side',async()=>{
 const storage=new Storage(),quota=new AiQuota({storage}),day=Math.floor(Date.now()/86400000);
 for(const [key,limit] of [[`user-day:${day}:${hash}`,20],[`ip-day:${day}:${ip}`,60],[`global:${day}`,200]]) {
  storage.values.clear();storage.values.set(key,limit);
  assert.equal((await reserve(quota,{phase:'generate',uid:hash,ip})).status,429);
 }
});
test('pre-authentication IP rate limit and cleanup retain no documents',async()=>{
 const storage=new Storage(),quota=new AiQuota({storage});
 for(let n=0;n<30;n++)assert.equal((await reserve(quota,{phase:'auth',ip})).status,204);
 assert.equal((await reserve(quota,{phase:'auth',ip})).status,429);
 storage.values.set('auth:0:'+ip,30);await quota.alarm();assert.equal(storage.values.has('auth:0:'+ip),false);
 assert.equal((await reserve(quota,{phase:'generate',uid:'invalid',ip})).status,400);
});
test('Worker rejects missing credentials before calling any provider',async()=>{
 const response=await worker.fetch(new Request('https://test/api/v1/ai/quizzes',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'}),{});
 assert.equal(response.status,401);
});
test('legacy admin access is retained but new generation fails closed without quota binding',async()=>{
 const env={TESTHELPER_ACCESS_TOKEN:'unit-test-only',GEMINI_API_KEY:'fake-provider-key'};
 const response=await worker.fetch(new Request('https://test/api/v1/ai/quizzes',{method:'POST',headers:{Authorization:'Bearer unit-test-only','Content-Type':'application/json'},body:JSON.stringify({topic:'Физика',count:1})}),env);
 assert.equal(response.status,503);assert.ok(!(await response.text()).includes(env.GEMINI_API_KEY));
});
test('public auth config fails closed until the owner enables Firebase',async()=>{
 assert.equal((await worker.fetch(new Request('https://test/api/v1/auth/config'),{})).status,503);
});

test('duplicate generation ID is rejected without spending a second quota',async()=>{
 const storage=new Storage(),quota=new AiQuota({storage}),requestId=crypto.randomUUID();
 assert.equal((await reserve(quota,{phase:'generate',uid:hash,ip,requestId})).status,204);
 assert.equal((await reserve(quota,{phase:'generate',uid:hash,ip,requestId})).status,409);
 const day=Math.floor(Date.now()/86400000);
 assert.equal(storage.values.get(`global:${day}`),1);
});
test('deletion page has strict CSP and never places credentials in URL or storage',async()=>{
 const response=await worker.fetch(new Request('https://test/delete-account'),{});
 const html=await response.text();
 assert.equal(response.status,200);assert.ok(response.headers.get('content-security-policy').includes("frame-ancestors 'none'"));
 assert.ok(html.includes("fetch('/api/v1/account'"));assert.ok(!html.includes('localStorage'));assert.ok(!html.includes('GEMINI_API_KEY'));
});
test('Worker rejects oversized JSON, wrong content type and client-supplied privilege fields',async()=>{
 const env={TESTHELPER_ACCESS_TOKEN:'unit-test-only',GEMINI_API_KEY:'fake-key'};
 for(const [contentType,body,status] of [['text/plain','{}',415],['application/json','x'.repeat(65537),413],['application/json',JSON.stringify({topic:'test',admin:true}),422]]) {
  const r=await worker.fetch(new Request('https://test/api/v1/ai/quizzes',{method:'POST',headers:{Authorization:'Bearer unit-test-only','Content-Type':contentType},body}),env);
  assert.equal(r.status,status);
 }
});
for(const [route,payload,modelResult] of [
 ['quizzes',{topic:'Ток',count:1,difficulty:'easy'},{title:'Физика',questions:[{text:'Единица тока?',options:['Ампер','Вольт'],correctIndex:0,explanation:'Сила тока измеряется в амперах.'}]}],
 ['flashcards',{topic:'Ток',count:1,difficulty:'easy'},{cards:[{front:'Единица тока?',back:'Ампер'}]}],
 ['review',{material:'Ошибка: единица тока — Ом, правильно Ампер'},{summary:'Повторите единицы',nextSteps:['Разобрать силу тока']}],
]) test('authenticated Worker integration: '+route,async()=>{
 const originalFetch=globalThis.fetch;
 const storage=new Storage(),quotaObject=new AiQuota({storage});
 const env={FIREBASE_PROJECT_ID:project,FIREBASE_WEB_API_KEY:'fake-public-key',GEMINI_API_KEY:'fake-private-key',AI_QUOTA:{idFromName:name=>name,get:()=>({fetch:(url,init)=>quotaObject.fetch(new Request(url,init))})}};
 const current=Math.floor(Date.now()/1000);
 const idToken=await token({...claims,iat:current-1,exp:current+3500,auth_time:current-5});
 let providerCalls=0;
 globalThis.fetch=async(url,init)=>{
  assert.equal(init.redirect,'manual','Workers supports manual redirects, not error');
  if(String(url).includes('/service_accounts/'))return Response.json({keys:[jwk]});
  if(String(url).includes('accounts:lookup'))return Response.json({users:[{localId:'user-a',emailVerified:true}]});
  assert.ok(String(url).includes('gemini-3.5-flash-lite:generateContent'));assert.equal(init.headers['x-goog-api-key'],'fake-private-key');providerCalls++;
  return Response.json({candidates:[{finishReason:'STOP',content:{parts:[{text:JSON.stringify(modelResult)}]}}]});
 };
 try {
  const response=await worker.fetch(new Request('https://test/api/v1/ai/'+route,{method:'POST',headers:{Authorization:'Bearer '+idToken,'Content-Type':'application/json','X-Request-ID':crypto.randomUUID()},body:JSON.stringify(payload)}),env);
  assert.equal(response.status,200);const body=await response.text();assert.ok(!body.includes(env.GEMINI_API_KEY));assert.ok(!body.includes(idToken));assert.equal(providerCalls,1);
 } finally {globalThis.fetch=originalFetch;}
});
for(const providerSucceeds of [true,false]) test('account deletion only clears quotas after provider success: '+providerSucceeds,async()=>{
 const originalFetch=globalThis.fetch,storage=new Storage(),quotaObject=new AiQuota({storage});
 const uid=await digest('user-a'),day=Math.floor(Date.now()/86400000);
 storage.values.set(`user-day:${day}:${uid}`,20);storage.values.set(`global:${day}`,8);
 const env={FIREBASE_PROJECT_ID:project,FIREBASE_WEB_API_KEY:'fake-public-key',AI_QUOTA:{idFromName:name=>name,get:()=>({fetch:(url,init)=>quotaObject.fetch(new Request(url,init))})}};
 const current=Math.floor(Date.now()/1000),idToken=await token({...claims,email_verified:false,iat:current-1,exp:current+3500,auth_time:current-5});
 let deletes=0;
 globalThis.fetch=async(url,init)=>{
  assert.equal(init.redirect,'manual','Workers supports manual redirects, not error');
  if(String(url).includes('/service_accounts/'))return Response.json({keys:[jwk]});
  if(String(url).includes('accounts:lookup'))return Response.json({users:[{localId:'user-a',emailVerified:false}]});
  assert.ok(String(url).includes('accounts:delete'));deletes++;
  return Response.json({}, {status:providerSucceeds?200:400});
 };
 try {
  const response=await worker.fetch(new Request('https://test/api/v1/account',{method:'DELETE',headers:{Authorization:'Bearer '+idToken}}),env);
  assert.equal(response.status,providerSucceeds?200:401);assert.equal(deletes,1);
  assert.equal(storage.values.has(`user-day:${day}:${uid}`),!providerSucceeds);
  assert.equal(storage.values.get(`global:${day}`),8);
 } finally {globalThis.fetch=originalFetch;}
});