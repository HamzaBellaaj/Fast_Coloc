/** Local fixture bridge for instrumentation. Never expose it on the LAN or log credentials. */
const { readFileSync, writeFileSync } = require('node:fs');
const { createServer } = require('node:http');
const { randomUUID } = require('node:crypto');
const { join } = require('node:path');
const config = Object.fromEntries(readFileSync(join(__dirname, '../.env.local'),'utf8').split(/\r?\n/)
  .filter(line => /^[A-Z_]+=/.test(line)).map(line => { const i=line.indexOf('='); return [line.slice(0,i),line.slice(i+1).trim()]; }));
const upstream='https://nklsbubnyznlfpxpxsrp.supabase.co';
const run=randomUUID();
const fixture={email:`fastcoloc-mobile-${run}@example.com`,webEmail:`fastcoloc-crossweb-${run}@example.com`,password:'Fc!aA9'+randomUUID(),newPassword:'Fc!aA9'+randomUUID()};
const owned=new Map(); const checks={}; let shuttingDown=false;
async function browserCheck(email,password,id,edit) {
 const {chromium}=require(join(__dirname,'../../frontend/node_modules/playwright'));
 const browser=await chromium.launch({headless:true,channel:'chrome'});
 let stage='login';
 try {
  const page=await browser.newPage();
  await page.goto('http://localhost:4200/login');
  await page.getByLabel('Adresse e-mail',{exact:true}).fill(email);
  await page.getByLabel('Mot de passe',{exact:true}).fill(password);
  await page.getByRole('button',{name:'Se connecter →',exact:true}).click();
  await page.waitForURL(/\/(profile|register\/\d)$/,{timeout:45000});
  stage='identity';
  const actual=await page.evaluate(async()=>{
   const session=JSON.parse(sessionStorage.getItem('fastcoloc.session.v1'));
   const r=await fetch('/api/v1/users/me',{headers:{Authorization:'Bearer '+session.access_token}});
   if(!r.ok)throw Error('Profile unavailable');return (await r.json()).id;
  });
  if(actual!==id)throw Error('Browser UUID mismatch');
  if(edit) {
   stage='read-android-profile';await page.goto('http://localhost:4200/profile');
   await page.getByText('DepuisAndroid',{exact:false}).first().waitFor();
   await page.screenshot({path:join(__dirname,'../../frontend/test-results/screenshots/mobile-cross-web.png'),fullPage:true});
   checks.angularBrowserReadsAndroidData=true;
   stage='edit-web-profile';await page.goto('http://localhost:4200/register/1');
   await page.getByLabel('Nom',{exact:true}).fill('DepuisWeb');
   await page.getByRole('button',{name:'Enregistrer et continuer →',exact:true}).click();
   await page.waitForURL('**/register/2');checks.angularBrowserUpdatesProfile=true;
  }
 }catch(_){throw Error('Browser cross-platform validation failed at '+stage);}
 finally {await browser.close();}
}
async function operator(method,path,body) {
  const r=await fetch(upstream+path,{method,headers:{apikey:config.FASTCOLOC_SUPABASE_SECRET_KEY,Authorization:'Bearer '+config.FASTCOLOC_SUPABASE_SECRET_KEY,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
  if(!r.ok && r.status!==404) throw Error('Fixture operator HTTP '+r.status);
  return r.status===204||r.status===404?{}:r.json();
}
async function backend(method,path,body,token) {
 const r=await fetch('http://127.0.0.1:8001/api/v1'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});
 if(!r.ok) throw Error('Fixture API HTTP '+r.status);
 return r.status===204?{}:r.json();
}
async function cleanup() {
 if(shuttingDown)return;shuttingDown=true;
 // Discover an owned signup even if instrumentation failed before confirmation.
 const listing=await operator('GET','/auth/v1/admin/users?page=1&per_page=1000');
 for(const user of listing.users||[]) if(user.email===fixture.email)owned.set(user.id,fixture.email);
 let cleaned=0;
 for(const [id,email] of owned) {
  const actual=await operator('GET','/auth/v1/admin/users/'+id);
  const user=actual.user||actual;
  if(user.id && user.email!==email) throw Error('Fixture ownership mismatch');
  const files=await operator('POST','/storage/v1/object/list/profile-photos',{prefix:id+'/',limit:100,offset:0});
  if(Array.isArray(files)&&files.length) await operator('DELETE','/storage/v1/object/profile-photos',{prefixes:files.map(f=>id+'/'+f.name)});
  await operator('DELETE','/auth/v1/admin/users/'+id);cleaned++;
 }
 writeFileSync(join(__dirname,'../target/mobile-cross-report.json'),JSON.stringify({date:new Date().toISOString(),checks,fixturesCleaned:cleaned},null,2));
 console.log('Mobile recipe cleaned '+cleaned+' owned fixtures.');
}
const server=createServer(async(req,res)=>{
 res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-store');
 try {
  if(req.method!=='GET') {res.writeHead(405);res.end('{}');return;}
  if(req.url==='/health') {res.end('{"ready":true}');return;}
  if(req.url==='/fixture') {res.end(JSON.stringify(fixture));return;}
  if(req.url==='/confirm-mobile') {
   const result=await operator('GET','/auth/v1/admin/users?page=1&per_page=1000');
   const user=result.users.find(u=>u.email===fixture.email);
   if(!user)throw Error('Owned signup not found');
   owned.set(user.id,fixture.email);
   const generated=await operator('POST','/auth/v1/admin/generate_link',{type:'signup',email:fixture.email,password:fixture.password,redirect_to:'fastcoloc://auth/callback'});
   const action=generated.action_link||generated.properties?.action_link;
   if(!action)throw Error('Signup fixture link unavailable');
   const verified=await fetch(action,{redirect:'manual'});
   const callback=verified.headers.get('location');
   if(verified.status<300||verified.status>=400||!callback)throw Error('Signup link did not redirect');
   const native=new URL(callback);
   const fragment=new URLSearchParams(native.hash.slice(1));
   if(native.protocol!=='fastcoloc:'||native.hostname!=='auth'||native.pathname!=='/callback'||!fragment.has('access_token')||!fragment.has('refresh_token'))throw Error('Signup link did not return a native Auth session');
   checks.androidSignupFound=true;checks.emailVerificationRedirectsToAndroid=true;
   res.end(JSON.stringify({callback}));return;
  }
  if(req.url==='/recovery-link') {
   const result=await operator('POST','/auth/v1/admin/generate_link',{type:'recovery',email:fixture.email,redirect_to:'fastcoloc://auth/callback'});
   const hash=result.hashed_token||result.properties?.hashed_token;
   if(!hash)throw Error('Recovery fixture hash unavailable');
   res.end(JSON.stringify({tokenHash:hash}));return;
  }
  if(req.url==='/web-check'||req.url==='/mobile-check'||req.url==='/reset-check') {
   const mobile=req.url!=='/web-check';
   const session=await backend('POST','/auth/login',{email:mobile?fixture.email:fixture.webEmail,password:req.url==='/reset-check'?fixture.newPassword:fixture.password});
   const details=await backend('GET','/users/me/details',undefined,session.access_token);
   if(!owned.has(details.profile.id)) throw Error('Foreign profile');
   if(mobile) {
    checks.androidAccountLogsInThroughWebApi=true;
    await browserCheck(fixture.email,req.url==='/reset-check'?fixture.newPassword:fixture.password,details.profile.id,false);
    if(req.url==='/reset-check'){checks.androidPasswordResetVisibleOnWeb=true;checks.angularBrowserResetPasswordLogin=true;}
    else checks.angularBrowserLoginFromMobileSignup=true;
    res.end(JSON.stringify({id:details.profile.id}));return;
   }
   if(!details.profile.completedAt||details.profile.lastName!=='DepuisAndroid'||details.photos.length!==1)throw Error('Mobile data not visible through Web API');
   checks.androidProfileVisibleOnWeb=true;checks.privatePhotoVisibleOnWeb=true;
   await browserCheck(fixture.webEmail,fixture.password,details.profile.id,true);
   checks.webProfileWritten=true;
   res.end(JSON.stringify({id:details.profile.id}));return;
  }
  if(req.url==='/finish') {await cleanup();res.end('{}');server.close();return;}
  res.writeHead(404);res.end('{}');
 }catch(e){console.error('Recipe request failed: '+e.message);res.writeHead(500);res.end('{"error":"fixture request failed"}');}
});
(async()=>{
 const user=await operator('POST','/auth/v1/admin/users',{email:fixture.webEmail,password:fixture.password,email_confirm:true,user_metadata:{fastcoloc_mobile_recipe:run}});
 if(!/^[0-9a-f-]{36}$/.test(user.id))throw Error('Invalid fixture id');
 owned.set(user.id,fixture.webEmail);checks.webFixtureCreated=true;
 server.listen(8123,'127.0.0.1',()=>console.log('Mobile recipe bridge ready on loopback:8123.'));
})().catch(async e=>{console.error(e.message);await cleanup();process.exitCode=1;});
for(const signal of ['SIGINT','SIGTERM']) process.on(signal,async()=>{await cleanup();server.close();});
