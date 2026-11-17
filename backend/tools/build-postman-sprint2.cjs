const fs=require('node:fs'),path=require('node:path');const root=path.resolve(__dirname,'..');
const collection=JSON.parse(fs.readFileSync(path.join(root,'postman/FastColoc-Sprint1.postman_collection.json'),'utf8'));
collection.info.name='FastColoc Sprints 1 et 2 — API Spring Boot';
collection.info.description+=' Sprint 2 : exécuter le dossier dédié avec un compte de recette ACTIVE, confirmé et complet. Le dossier crée puis supprime uniquement sa propre annonce.';
function request(name,method,route,status,body,extra=[]){return{name,request:{method,url:'{{base_url}}/api/v1'+route,auth:{type:'bearer',bearer:[{key:'token',value:'{{access_token}}',type:'string'}]},header:body?[{key:'Content-Type',value:'application/json'}]:[],...(body?{body:{mode:'raw',raw:JSON.stringify(body,null,2),options:{raw:{language:'json'}}}}:{})},event:[{listen:'test',script:{type:'text/javascript',exec:[`pm.test('HTTP ${status}',()=>pm.response.to.have.status(${status}));`,...extra]}}]};}
const body={kind:'OFFER',title:'[TEST POSTMAN] Annonce temporaire',description:'Cette annonce sert uniquement à la recette automatisée et sera supprimée.',localityId:'{{locality_id}}',street:'Adresse temporaire de recette',latitude:36.8,longitude:10.18,rent:350.125,capacity:2,propertyType:'Appartement',roommateType:'Étudiant',equipment:['WiFi','Cuisine'],availableFrom:'2026-01-01',availableUntil:null,publicationDays:30,version:null};
collection.item.push({name:'Sprint 2 — Annonces',item:[
 request('Catalogue public','GET','/listings?size=1',200,null,["pm.test('Pagination réelle',()=>pm.expect(pm.response.json().size).to.equal(1));"]),
 request('Référentiel géographique','GET','/listings/areas',200),
 request('Créer mon brouillon','POST','/listings',201,body,["if(pm.response.code===201)pm.environment.set('listing_id',pm.response.json().id);","pm.test('Brouillon serveur',()=>pm.expect(pm.response.json().status).to.equal('DRAFT'));"]),
 request('Consulter mon brouillon','GET','/listings/{{listing_id}}',200),
 {name:'Photo via Spring Boot',request:{method:'POST',url:'{{base_url}}/api/v1/listings/{{listing_id}}/photos',auth:{type:'bearer',bearer:[{key:'token',value:'{{access_token}}',type:'string'}]},body:{mode:'formdata',formdata:[{key:'file',type:'file',src:'tools/fixtures/profile.png'},{key:'caption',type:'text',value:'Image technique de recette'}]}},event:[{listen:'test',script:{type:'text/javascript',exec:["pm.test('HTTP 200',()=>pm.response.to.have.status(200));","if(pm.response.code===200)pm.environment.set('listing_photo_id',pm.response.json().photos[0].id);"]}}]},
 request('Publier mon offre','POST','/listings/{{listing_id}}/publish',200),
 request('Recherche multicritère','GET','/listings?kind=OFFER&minRent=300&maxRent=400&equipment=WiFi&sort=RENT_ASC&size=5',200),
 request('Positions cartographiques filtrées','GET','/listings/map?kind=OFFER&minRent=300&maxRent=400&size=50',200,null,["pm.test('Marqueurs des offres',()=>pm.expect(pm.response.json().items.every(row=>row.kind==='OFFER'&&typeof row.latitude==='number')).to.equal(true));"]),
 request('Ajouter aux favoris','PUT','/listings/{{listing_id}}/favorite',204),
 request('Mes favoris','GET','/listings/favorites',200),
 request('Mes annonces','GET','/listings/mine',200),
 request('Modifier ma propre annonce','PUT','/listings/{{listing_id}}',200,{...body,rent:400,version:1}),
 request('Renouveler','POST','/listings/{{listing_id}}/renew',200),
 request('Retirer des favoris','DELETE','/listings/{{listing_id}}/favorite',204),
 request('Retirer la photo','DELETE','/listings/{{listing_id}}/photos/{{listing_photo_id}}',204),
 request('Supprimer ma propre annonce','DELETE','/listings/{{listing_id}}',204),
 request('Suppression effective','GET','/listings/{{listing_id}}',404)
]});
fs.writeFileSync(path.join(root,'postman/FastColoc-Sprint2.postman_collection.json'),JSON.stringify(collection,null,2)+'\n');
console.log('Sprint 2 Postman collection generated without credentials.');
