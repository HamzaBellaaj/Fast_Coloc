/** Local-only cross-platform test bridge. Run only while the emulator recipe is active. */
const {createServer}=require('node:http');const {join}=require('node:path');const {writeFileSync}=require('node:fs');
const {chromium,expect:baseExpect}=require(join(__dirname,'../../frontend/node_modules/@playwright/test'));
const expect=baseExpect.configure({timeout:45000});
const recipe=require('./sprint2-fixtures.cjs');let fixture,browser,ownerPage,readerPage;const checks={};let finished=false;
async function start(){
 fixture=await recipe.fixtures();browser=await chromium.launch({headless:true,channel:'chrome'});ownerPage=await browser.newPage();
 await recipe.webCreate(ownerPage,fixture,expect);checks.webCreatesListingAndPhoto=true;
 readerPage=await browser.newPage();await recipe.login(readerPage,fixture.reader);
 createServer(async(req,res)=>{try{
  let result={ready:true};const path=req.url;
  if(path==='/fixture')result={ownerEmail:fixture.owner.email,ownerPassword:fixture.owner.password,readerEmail:fixture.reader.email,readerPassword:fixture.reader.password,ownerId:fixture.owner.id,readerId:fixture.reader.id,webId:fixture.webListingId,webTitle:fixture.webTitle,nativeTitle:fixture.nativeTitle,localityId:fixture.area.id};
  else if(path==='/check-favorite'){
   await readerPage.goto('http://localhost:4200/favoris');await expect(readerPage.getByRole('heading',{name:fixture.webTitle,exact:true})).toBeVisible();checks.androidFavoriteVisibleOnWeb=true;
  }else if(path==='/check-unfavorite'){
   await readerPage.reload();await expect(readerPage.getByRole('heading',{name:fixture.webTitle,exact:true})).toHaveCount(0);checks.androidUnfavoriteVisibleOnWeb=true;
  }else if(path==='/check-native'){
   const rows=await recipe.api('GET','/listings/mine?size=50',fixture.owner.session.access_token);const native=rows.items.find(r=>r.title===fixture.nativeTitle);if(!native)throw Error('Native listing missing');fixture.nativeId=native.id;
   await ownerPage.goto('http://localhost:4200/annonces/'+native.id);await expect(ownerPage.getByRole('heading',{name:fixture.nativeTitle,exact:true})).toBeVisible();await expect(ownerPage.locator('.listing-gallery img')).toHaveCount(1);checks.androidCreationVisibleOnWeb=true;checks.androidPhotoVisibleOnWeb=true;
   await recipe.webEdit(ownerPage,native.id,700.5);checks.webUpdatesNativeListing=true;result={ready:true,nativeId:native.id};
  }else if(path==='/check-native-update'){
   await ownerPage.goto('http://localhost:4200/annonces/'+fixture.nativeId);await expect(ownerPage.locator('.rent')).toContainText('710.5');checks.androidUpdateVisibleOnWeb=true;
  }else if(path==='/check-delete'){
   const r=await fetch('http://127.0.0.1:8001/api/v1/listings/'+fixture.nativeId,{headers:{Authorization:'Bearer '+fixture.reader.session.access_token}});if(r.status!==404)throw Error('Deleted listing remains readable');checks.androidDeletionShared=true;
  }else if(path==='/finish'){
   if(!finished){finished=true;await browser.close();checks.fixturesCleaned=await recipe.cleanup();writeFileSync(join(__dirname,'../target/sprint2-cross-report.json'),JSON.stringify(checks,null,2));setTimeout(()=>process.exit(),300)}result=checks;
  }else if(path!=='/health'){res.writeHead(404);res.end('{}');return;}
  res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(result));
 }catch(e){console.error('Recipe step failed:',req.url,e.message);res.writeHead(500,{'Content-Type':'application/json'});res.end(JSON.stringify({error:'Recipe step failed'}))}}).listen(8124,'127.0.0.1',()=>console.log('Sprint 2 fixture bridge ready'));
}
start().catch(async e=>{console.error('Recipe startup failed:',e.message);try{await browser?.close();await recipe.cleanup()}catch{console.error('Fixture cleanup requires inspection')}process.exitCode=1});
