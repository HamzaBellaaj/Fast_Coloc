import {test,expect,Page} from '@playwright/test';
const profile={id:'guest',firstName:'Sarah',lastName:'Fixture',role:'USER',status:'ACTIVE',completedAt:'2026-11-23T12:00:00Z',validatedSteps:[1,2,3,4,5]};
const conversation={id:'conversation-1',listing_id:'listing-1',listing_reference:'listing-1',listing_title:'Chambre à Ariana',listing_kind:'OFFER',locality:'Ariana',rent:450,participant_id:'owner',participant_name:'Ahmed',last_message:'Bonjour',unread_count:1,listing_available:true,created_at:'2026-11-23T12:00:00Z',updated_at:'2026-11-23T12:00:00Z'};
async function fixture(page:Page,newConversation=false,failOnce=false){
 let exists=!newConversation;const sent:string[]=[];const messages=[{id:1,conversation_id:conversation.id,sender_id:'owner',client_id:'initial',content:'Bonjour, la chambre est disponible.',created_at:'2026-11-23T12:00:00Z'}];
 await page.addInitScript(()=>sessionStorage.setItem('fastcoloc.session.v1',JSON.stringify({access_token:'ui-fixture-only',refresh_token:'ui-fixture-only',expires_at:Math.floor(Date.now()/1000)+3600,expires_in:3600})));
 await page.route('**/api/v1/**',async route=>{
  const request=route.request(),url=new URL(request.url()),p=url.pathname.replace('/api/v1','');let body:any={};let status=200;
  if(p==='/auth/session')body={emailConfirmed:true,email:'fixture@example.invalid'};
  else if(p==='/users/sync')body=profile;
  else if(p==='/conversations'&&request.method()==='GET')body={items:exists?[conversation]:[],total:exists?1:0,page:0,size:20};
  else if(p==='/listings/listing-1')body={id:'listing-1',title:conversation.listing_title,author_id:'owner',kind:'REQUEST',description:'Une chambre dans une colocation agréable.',locality:'Ariana',governorate:'Ariana',rent:450,status:'PUBLISHED',is_hidden:false,street:'Ariana',property_type:'Appartement',capacity:1,equipment:[],photos:[],available_from:'2026-11-23',expires_at:'2026-12-23T12:00:00Z',favorite:false};
  else if(p==='/conversations'&&request.method()==='POST'){const data=request.postDataJSON();sent.push(data.clientId);exists=true;messages.push({id:2,conversation_id:conversation.id,sender_id:'guest',client_id:data.clientId,content:data.content,created_at:'2026-11-23T12:01:00Z'});body=conversation;status=201;}
  else if(p==='/conversations/conversation-1')body=conversation;
  else if(p.endsWith('/messages')&&request.method()==='GET')body={items:messages,hasMore:false,nextBefore:1};
  else if(p.endsWith('/messages')&&request.method()==='POST'){const data=request.postDataJSON();sent.push(data.clientId);if(failOnce&&sent.length===1){status=503;body={code:'UNAVAILABLE'};}else{const message={id:2,conversation_id:conversation.id,sender_id:'guest',client_id:data.clientId,content:data.content,created_at:'2026-11-23T12:01:00Z'};messages.push(message);body=message;}}
  else if(p.endsWith('/read'))status=204;
  else {status=404;body={code:'FIXTURE_NOT_FOUND'};}
  await route.fulfill({status,contentType:'application/json',body:status===204?'':JSON.stringify(body)});
 });return sent;
}
test('inbox opens a private discussion, sends text safely and renders unread state',async({page})=>{
 const sent=await fixture(page);await page.goto('/messages');await page.getByRole('link',{name:/Ahmed/}).click();
 await expect(page.getByText('Bonjour, la chambre est disponible.',{exact:true}).last()).toBeVisible();
 await page.getByLabel('Ton message').fill('<img src=x onerror=alert(1)>');await page.getByRole('button',{name:'Envoyer',exact:true}).click();
 await expect(page.locator('.message p').last()).toHaveText('<img src=x onerror=alert(1)>');expect(sent).toHaveLength(1);await expect(page.locator('.history img')).toHaveCount(0);
 await page.screenshot({path:'test-results/messaging-desktop.png',fullPage:true});
});
test('contact composer creates a conversation only on first send',async({page})=>{
 const sent=await fixture(page,true);await page.goto('/messages?listing=listing-1');await expect(page.getByLabel('Ton message')).toBeVisible();expect(sent).toHaveLength(0);
 await page.getByLabel('Ton message').fill('Une visite demain ?');await page.getByRole('button',{name:'Envoyer',exact:true}).click();await expect(page).toHaveURL(/messages\/conversation-1$/);await expect(page.locator('.message p').last()).toHaveText('Une visite demain ?');expect(sent).toHaveLength(1);
});
test('mobile composer preserves a failed message and retries with the same request id',async({page})=>{
 await page.setViewportSize({width:390,height:844});const sent=await fixture(page,false,true);await page.goto('/messages/conversation-1');const field=page.getByLabel('Ton message');await field.fill('Je suis intéressée.');await page.getByRole('button',{name:'Envoyer',exact:true}).click();await expect(page.getByRole('alert')).toBeVisible();await expect(field).toHaveValue('Je suis intéressée.');await page.getByRole('button',{name:'Envoyer',exact:true}).click();await expect(field).toHaveValue('');expect(sent).toHaveLength(2);expect(sent[0]).toBe(sent[1]);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:'test-results/messaging-mobile.png',fullPage:true});
});

test('a housing listing opens the messaging composer through Contacter',async({page})=>{
 const sent=await fixture(page,true);await page.goto('/annonces/listing-1');
 await expect(page.getByRole('link',{name:'Messages',exact:true})).toBeVisible();
 const contact=page.getByRole('link',{name:'Contacter',exact:true});await expect(contact).toBeVisible();await contact.click();
 await expect(page).toHaveURL(/messages\?listing=listing-1$/);await expect(page.getByLabel('Ton message')).toBeVisible();expect(sent).toHaveLength(0);
 await page.getByLabel('Ton message').fill('Bonjour, votre annonce est-elle disponible ?');await page.getByRole('button',{name:'Envoyer',exact:true}).click();
 await expect(page).toHaveURL(/messages\/conversation-1$/);expect(sent).toHaveLength(1);
});
