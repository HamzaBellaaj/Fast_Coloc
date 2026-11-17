/** Persistent demo data through the real API. Reruns resume without duplicating listings.
 * Server credentials stay in .env.local; demo credentials and progress stay in ignored target/.
 * Run with the backend active: node tools/seed-demo-listings.cjs
 */
const {readFileSync,writeFileSync,existsSync,mkdirSync}=require('node:fs');
const {join}=require('node:path');
const {randomUUID}=require('node:crypto');
const {api,operator}=require('./sprint2-fixtures.cjs');
const folder=join(__dirname,'../target/demo-listings');mkdirSync(folder,{recursive:true});
const ledger=join(folder,'progress.json');
const state=existsSync(ledger)?JSON.parse(readFileSync(ledger,'utf8')):{accounts:{},listings:{}};
const save=()=>writeFileSync(ledger,JSON.stringify(state,null,2));
const people=[['Amine','Ben Salah'],['Yasmine','Trabelsi'],['Aziz','Mansour'],['Ines','Gharbi'],['Sarra','Ayari'],['Mohamed','Hamdi'],['Nour','Saidi'],['Youssef','Mrad']];
// Illustrative interiors, never represented as photographs of real Tunisian properties.
const photoIds=['1502672260266-1c1ef2d93688','1522708323590-d24dbb6b0267','1493809842364-78817add7ffb','1484154218962-a197022b5858','1560448204-e02f11c3d0e2','1554995207-c18c203602cb','1512917774080-9991f1c4c750','1600585154340-be6161a56a0c'];
const locations=[
 ['Tunis',36.8065,10.1815,'Centre-ville'],['La Marsa',36.8782,10.3247,'Quartier résidentiel'],
 ['Aryanah',36.8665,10.1647,'Près des transports'],['Ben Arous',36.7531,10.2283,'Quartier calme'],
 ['Sousse',35.8256,10.6369,'Proche du campus'],['Sfax',34.7406,10.7603,'Centre-ville'],
 ['Nabeul',36.4561,10.7376,'Proche des commerces'],['Monastir',35.7643,10.8113,'Quartier universitaire'],
 ['Le Bardo',36.8092,10.1406,'Près du métro'],['Carthage',36.8531,10.3231,'Quartier résidentiel'],
 ['La Goulette',36.8183,10.3052,'Près de la gare'],['Le Kram',36.8332,10.3156,'Proche des commerces']
];
const offers=[
 'Chambre lumineuse dans une coloc étudiante', 'Une place dans notre appartement avec terrasse',
 'Coloc calme pour jeune actif, chambre meublée', 'Deux chambres libres dans une maison conviviale',
 'Rejoins notre coloc près du campus', 'Chambre avec bureau, idéale pour télétravailler',
 'Maison avec jardin : on cherche un coloc', 'Appartement cosy, une chambre se libère',
 'Coloc petit budget près du métro', 'Chambre spacieuse dans un S+3 équipé',
 'Coloc entre jeunes actifs près des transports', 'Une place dans une maison avec terrasse'
];
const requests=[
 'Étudiant cherche une chambre près des transports', 'Jeune développeuse cherche une coloc calme',
 'Deux amis cherchent un appartement à partager', 'Recherche une chambre meublée pour un stage',
 'Étudiante cherche une colocation près du campus', 'Jeune actif cherche une maison à partager',
 'Recherche coloc conviviale avec espace de travail', 'Budget étudiant : je cherche une chambre',
 'Nouveau travail : recherche une coloc longue durée', 'Cherche un S+2 à partager avec un coloc',
 'Étudiant en master cherche une chambre au calme', 'Recherche une colocation pour deux personnes'
];
async function image(index){
 const id=photoIds[index%photoIds.length], path=join(folder,id+'.jpg');
 if(!existsSync(path)){
  const r=await fetch(`https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=1000&q=80`,{signal:AbortSignal.timeout(30000)});
  if(!r.ok)throw Error('Photo download HTTP '+r.status);
  writeFileSync(path,Buffer.from(await r.arrayBuffer()));
 }
 return readFileSync(path);
}
async function upload(path,token,bytes,caption){const form=new FormData();form.append('file',new Blob([bytes],{type:'image/jpeg'}),'illustration.jpg');form.append('caption',caption);return api('POST',path,token,form);}
async function account(index,areas,photos){
 const [firstName,lastName]=people[index];let user=state.accounts[index];
 if(!user){
  const email=`fastcoloc-demo-${firstName.toLowerCase()}-${randomUUID().slice(0,8)}@example.com`,password='Fc!aA9'+randomUUID();
  const created=await operator('POST','/auth/v1/admin/users',{email,password,email_confirm:true,user_metadata:{fastcoloc_demo:true}});
  if(!created.id)throw Error('Demo user creation failed');
  user=state.accounts[index]={id:created.id,email,password};save();
 }
 const session=await api('POST','/auth/login',null,{email:user.email,password:user.password});const token=session.access_token;
 if(!user.complete){
  const area=areas.find(a=>a.locality===locations[index][0]);if(!area)throw Error('Locality missing');
  await api('POST','/users/sync',token,{});
  await api('PUT','/onboarding/steps/1',token,{firstName,lastName,age:21+index,gender:null});
  await api('PUT','/onboarding/steps/2',token,{countryCode:'TN',governorateId:area.governorate_id,localityId:area.id,street:'Profil fictif de démonstration',postalCode:'1000'});
  await api('PUT','/onboarding/steps/3',token,{mainActivities:[],hobbies:[],spokenLanguages:['fr'],religion:null,smoking:null});
  await api('PUT','/onboarding/steps/4',token,{mobilePhone:'20000000'});
  const photo=await upload('/profile-photos',token,photos[index],'Illustration de logement — profil fictif de démonstration');
  await api('PUT','/onboarding/steps/5',token,{photoIds:[photo.id]});
  await api('POST','/onboarding/complete',token);user.complete=true;save();
 }
 return {...user,token};
}
async function main(){
 const areas=await api('GET','/listings/areas');
 const photos=await Promise.all(photoIds.map((_,i)=>image(i)));
 const users=[];for(let i=0;i<people.length;i++){users.push(await account(i,areas,photos));console.log('Profil démo prêt : '+people[i].join(' '));}
 for(let i=0;i<24;i++){
  const offer=i%2===0, n=Math.floor(i/2), location=locations[n],user=users[i%users.length];
  const area=areas.find(a=>a.locality===location[0]);if(!area)throw Error('Locality missing: '+location[0]);
  const title=(offer?offers:requests)[n]+' à '+location[0];
  let row=state.listings[i];
  if(!row){
   const description=offer
    ? `Nous avons déjà le logement et cherchons ${n%3===0?'deux colocataires':'un colocataire'} pour partager le quotidien. ${n%2===0?'Chambre meublée avec bureau et rangements.':'Espaces communs agréables et cuisine équipée.'} Nous aimons les repas partagés, le respect des espaces et une ambiance calme en semaine. ${location[3]}, à ${location[0]}. Le montant indiqué correspond à la part mensuelle par personne. Présente-toi et indique tes dates pour organiser une visite.`
    : `Je cherche ${n%3===2?'avec un ami un appartement à partager':'une chambre dans une colocation'} à ${location[0]}. ${n%2===0?'Études et révisions en semaine, sorties et sport le week-end.':'Je commence une nouvelle activité et souhaite une installation durable.'} Je suis soigneux, sociable et respectueux des espaces communs. Budget mensuel maximum indiqué, idéalement près des transports et des commerces. Ouvert à rejoindre une coloc existante ou à trouver un logement ensemble.`;
   const created=await api('POST','/listings',user.token,{
    kind:offer?'OFFER':'REQUEST',title,description:description+'\n\nAnnonce fictive de démonstration FastColoc. Photos d’illustration Unsplash ; elles ne représentent pas un bien réel à cette adresse.',
    localityId:area.id,street:offer?location[3]+' (adresse de démonstration)':null,
    latitude:offer?location[1]:null,longitude:offer?location[2]:null,
    rent:offer?[280,480,350,320,300,420,380,450,250,550,330,400][n]:[250,450,600,300,280,400,380,220,450,550,300,650][n],
    capacity:offer?(n%3===0?4:3):(n%3===2?2:1),propertyType:(offer?[3,6,11].includes(n):n===5)?'Maison':'Appartement',
    roommateType:n%2===0?'Étudiant':'Jeune actif',equipment:offer?['WiFi','Cuisine équipée',...(n%2===0?['Meublé','Machine à laver']:['Terrasse','Climatisation']),...(n===6?['Jardin']:[])]:['WiFi','Cuisine équipée'],
    availableFrom:'2026-11-17',availableUntil:null,publicationDays:90,version:null
   });
   row=state.listings[i]={id:created.id,authorId:user.id,title,kind:offer?'OFFER':'REQUEST'};save();
  }
  let detail=await api('GET','/listings/'+row.id,user.token);
  const count=offer?3:2;
  for(let p=detail.photos.length;p<count;p++)await upload('/listings/'+row.id+'/photos',user.token,photos[(n+p)%photos.length],offer?'Intérieur illustratif — démonstration (Unsplash)':'Ambiance de logement recherchée — illustration, aucun logement proposé (Unsplash)');
  detail=await api('GET','/listings/'+row.id,user.token);
  if(detail.status!=='PUBLISHED')await api('POST','/listings/'+row.id+'/publish',user.token);
  row.published=true;save();console.log(`${i+1}/24 : ${row.kind} — ${title}`);
 }
 let verified=0,totalPhotos=0;
 for(const row of Object.values(state.listings)){
  const user=users.find(u=>u.id===row.authorId), detail=await api('GET','/listings/'+row.id,user.token);
  if(detail.status!=='PUBLISHED'||detail.author_id!==user.id||detail.photos.length<(row.kind==='OFFER'?3:2))throw Error('Demo listing verification failed');
  const r=await fetch(detail.photos[0].url,{signal:AbortSignal.timeout(15000)});if(!r.ok||!r.headers.get('content-type')?.startsWith('image/'))throw Error('Demo photo inaccessible');await r.arrayBuffer();
  totalPhotos+=detail.photos.length;verified++;
 }
 const report={verified,offers:12,requests:12,accounts:users.length,listingPhotos:totalPhotos,verifiedAt:new Date().toISOString()};
 writeFileSync(join(folder,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
}
main().catch(e=>{console.error(e.message);process.exitCode=1});
