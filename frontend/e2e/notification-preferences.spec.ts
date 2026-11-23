import {test,expect} from '@playwright/test';
test('profile persists notification opt-out and explains the local inbox',async({page})=>{
 let enabled=true;const profile={id:'notification-user',firstName:'Sarah',lastName:'Fixture',role:'USER',status:'ACTIVE',completedAt:'2026-11-23T12:00:00Z',validatedSteps:[1,2,3,4,5]};
 await page.addInitScript(()=>sessionStorage.setItem('fastcoloc.session.v1',JSON.stringify({access_token:'fixture-only',refresh_token:'fixture-only',expires_at:Math.floor(Date.now()/1000)+3600,expires_in:3600})));
 await page.route('**/api/v1/**',async route=>{const req=route.request(),p=new URL(req.url()).pathname;let body:any={};
 if(p.endsWith('/auth/session'))body={emailConfirmed:true,email:'fixture@example.invalid'};
 else if(p.endsWith('/users/sync'))body=profile;
 else if(p.endsWith('/users/me/details'))body={profile,address:{country_code:'TN',governorate_id:1,locality_id:1},personal:{},contacts:{},preferences:{},preferredLocalities:[],photos:[]};
 else if(p.endsWith('/notification-preferences')){if(req.method()==='PATCH')enabled=req.postDataJSON().newMessageEmail;body={newMessageEmail:enabled,deliveryAvailable:true,deliveryMode:'local'};}
 else if(p.includes('/geography/'))body=[{id:1,code:'TN',name:'Tunisie'}];
 await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/profile');await expect(page.getByRole('heading',{name:'Notifications par e-mail'})).toBeVisible();await expect(page.getByText(/Mode de test : les e-mails arrivent/)).toBeVisible();const toggle=page.getByRole('checkbox',{name:'Recevoir un e-mail pour les nouveaux messages privés'});await expect(toggle).toBeChecked();await toggle.uncheck();await page.getByRole('button',{name:'Enregistrer les notifications',exact:true}).click();await expect(page.getByText('Préférences enregistrées.',{exact:true})).toBeVisible();expect(enabled).toBe(false);await page.reload();await expect(toggle).not.toBeChecked();
});
