/** Isolated, labelled recipe fixtures. Server credentials never enter the browser or APK. */
const {readFileSync,writeFileSync}=require('node:fs');const {join}=require('node:path');const {randomUUID}=require('node:crypto');
const config=Object.fromEntries(readFileSync(join(__dirname,'../.env.local'),'utf8').split(/\r?\n/).filter(l=>/^[A-Z_]+=/.test(l)).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1).trim()]}));
const upstream='https://nklsbubnyznlfpxpxsrp.supabase.co';const base='http://127.0.0.1:8001/api/v1';
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=','base64');
const run=randomUUID();const owned=[];const ledger=join(__dirname,'../target/sprint2-fixtures-'+run+'.json');
async function operator(method,path,body){const r=await fetch(upstream+path,{method,headers:{apikey:config.FASTCOLOC_SUPABASE_SECRET_KEY,Authorization:'Bearer '+config.FASTCOLOC_SUPABASE_SECRET_KEY,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});if(!r.ok&&r.status!==404)throw Error('Fixture operator HTTP '+r.status);return r.status===204?{}:r.json();}
async function api(method,path,token,body){const form=body instanceof FormData;const r=await fetch(base+path,{method,headers:{...(form?{}:{'Content-Type':'application/json'}),...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:form?body:JSON.stringify(body)});if(!r.ok)throw Error('Recipe API '+method+' '+path.split('?')[0]+' HTTP '+r.status);return r.status===204?{}:r.json();}
async function account(label,area){
 const user={email:`fastcoloc-s2-${label}-${run}@example.com`,password:'Fc!aA9'+randomUUID()};
 const created=await operator('POST','/auth/v1/admin/users',{email:user.email,password:user.password,email_confirm:true});user.id=created.id;if(!user.id)throw Error('Fixture account missing');owned.push(user);writeFileSync(ledger,JSON.stringify(owned.map(({id,email})=>({id,email}))));
 user.session=await api('POST','/auth/login',null,{email:user.email,password:user.password});const token=user.session.access_token;
 await api('POST','/users/sync',token,{});
 await api('PUT','/onboarding/steps/1',token,{firstName:'RecetteS2',lastName:label,age:25,gender:null});
 await api('PUT','/onboarding/steps/2',token,{countryCode:'TN',governorateId:area.governorate_id,localityId:area.id,street:'Adresse de recette temporaire',postalCode:'1000'});
 await api('PUT','/onboarding/steps/3',token,{mainActivities:[],hobbies:[],spokenLanguages:['fr'],religion:null,smoking:null});
 await api('PUT','/onboarding/steps/4',token,{mobilePhone:'20000000'});
 const form=new FormData();form.append('file',new Blob([png],{type:'image/png'}),'fixture.png');form.append('caption','Image technique temporaire de recette');const photo=await api('POST','/profile-photos',token,form);
 await api('PUT','/onboarding/steps/5',token,{photoIds:[photo.id]});await api('POST','/onboarding/complete',token);
 console.log('Sprint 2 fixture ready:',label);return user;
}
async function fixtures(){const areas=await api('GET','/listings/areas');const area=areas.find(a=>a.governorate.toLowerCase()==='tunis')||areas[0];if(!area)throw Error('No geography');return {run,area,owner:await account('owner',area),reader:await account('reader',area),webTitle:'[TEST TEMPORAIRE] Coloc Web '+run.slice(0,8),nativeTitle:'[TEST TEMPORAIRE] Coloc Android '+run.slice(0,8)};}
async function login(page,user){await page.goto('http://localhost:4200/login');await page.getByLabel('Adresse e-mail',{exact:true}).fill(user.email);await page.getByLabel('Mot de passe',{exact:true}).fill(user.password);await page.getByRole('button',{name:'Se connecter →',exact:true}).click();await page.waitForURL(/\/profile$/);}
async function webCreate(page,fixture,expect){
 await login(page,fixture.owner);await page.goto('http://localhost:4200/annonces/nouvelle');
 await page.getByLabel('Titre',{exact:true}).fill(fixture.webTitle);await page.getByLabel('Description',{exact:true}).fill('Annonce technique temporaire pour la recette Web et Android. Elle sera supprimée après les tests.');await page.getByLabel('Type de logement',{exact:true}).fill('Appartement');await page.getByLabel('Type de colocataire recherché (facultatif)').fill('Étudiant');
 await page.getByRole('button',{name:'Continuer →',exact:true}).click();await page.getByRole('combobox',{name:'Localité',exact:true}).selectOption({label:fixture.area.governorate+' · '+fixture.area.locality});await page.getByLabel('Adresse',{exact:true}).fill('Adresse de recette temporaire');await page.getByLabel('Latitude',{exact:true}).fill('36.8');await page.getByLabel('Longitude',{exact:true}).fill('10.18');await page.getByRole('button',{name:'Continuer →',exact:true}).click();
 await page.getByLabel('Loyer mensuel (TND)',{exact:true}).fill('350.125');await page.getByLabel('Capacité (personnes)').fill('2');await page.getByLabel('Disponible dès le',{exact:true}).fill('2026-01-01');await page.getByLabel('Équipements (séparés par une virgule)').fill('WiFi, Cuisine');
 await page.getByRole('button',{name:'Enregistrer l’annonce',exact:true}).click();await page.waitForURL(/\/annonces\/[0-9a-f-]+\/modifier$/);fixture.webListingId=page.url().split('/').at(-2);
 await page.getByLabel('Légende de la prochaine photo').fill('Image technique de recette');await page.locator('input[type=file]').setInputFiles({name:'recette.png',mimeType:'image/png',buffer:png});await expect(page.locator('.listing-gallery img')).toBeVisible();
 await page.getByRole('button',{name:'Publier mon annonce',exact:true}).click();await page.waitForURL('**/annonces/'+fixture.webListingId);await expect(page.getByRole('heading',{name:fixture.webTitle,exact:true})).toBeVisible();
 return fixture.webListingId;
}
async function webEdit(page,id,rent){await page.goto('http://localhost:4200/annonces/'+id+'/modifier');await page.getByRole('button',{name:'Continuer →',exact:true}).click();await page.getByRole('button',{name:'Continuer →',exact:true}).click();await page.getByLabel('Loyer mensuel (TND)',{exact:true}).fill(String(rent));const saved=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/listings/'+id));await page.getByRole('button',{name:'Enregistrer l’annonce',exact:true}).click();const response=await saved;if(!response.ok())throw Error('Web listing edit failed');}
async function cleanup(){let count=0;
 for(const user of owned){const result=await operator('GET','/auth/v1/admin/users/'+user.id);const actual=result.user||result;if(actual.id&&actual.email!==user.email)throw Error('Fixture ownership mismatch');if(!actual.id)continue;
  const session=await api('POST','/auth/login',null,{email:user.email,password:user.password});let page=await api('GET','/listings/mine?size=50',session.access_token);for(const row of page.items)await api('DELETE','/listings/'+row.id,session.access_token);
  const photos=await operator('POST','/storage/v1/object/list/profile-photos',{prefix:user.id+'/',limit:100,offset:0});if(Array.isArray(photos)&&photos.length)await operator('DELETE','/storage/v1/object/profile-photos',{prefixes:photos.map(p=>user.id+'/'+p.name)});
  await operator('DELETE','/auth/v1/admin/users/'+user.id);count++;
 }
 writeFileSync(ledger,JSON.stringify({fixturesCleaned:count,ownedAccounts:owned.length}));return count;
}
module.exports={fixtures,cleanup,api,operator,login,webCreate,webEdit,png};
