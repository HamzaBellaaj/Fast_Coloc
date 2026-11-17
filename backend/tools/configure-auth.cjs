// Optional Management API configuration, NOT run without an explicit project-scoped management token.
// Read secrets from .env.local; never log the token, Google secret or API response body.
const fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..');const cfg={...process.env};
for(const line of fs.readFileSync(path.join(root,'.env.local'),'utf8').split(/\r?\n/)){const m=line.match(/^([A-Z_]+)=(.*)$/);if(m)cfg[m[1]]=m[2].trim();}
async function main(){
 const token=cfg.FASTCOLOC_MANAGEMENT_ACCESS_TOKEN;
 if(!token)throw new Error('Set FASTCOLOC_MANAGEMENT_ACCESS_TOKEN locally or configure Auth in the project dashboard. The Auth server key is not a Management API token.');
 const payload={site_url:cfg.FASTCOLOC_SITE_URL||'http://localhost:4200',uri_allow_list:cfg.FASTCOLOC_AUTH_CALLBACK_URL||'http://localhost:4200/auth/callback',mailer_autoconfirm:false,
  mailer_subjects_confirmation:'Confirmez votre adresse FastColoc',mailer_templates_confirmation_content:fs.readFileSync(path.join(root,'config/email-templates/confirmation.html'),'utf8'),
  mailer_subjects_recovery:'Réinitialisez votre mot de passe FastColoc',mailer_templates_recovery_content:fs.readFileSync(path.join(root,'config/email-templates/recovery.html'),'utf8')};
 if(cfg.FASTCOLOC_GOOGLE_CLIENT_ID&&cfg.FASTCOLOC_GOOGLE_CLIENT_SECRET)Object.assign(payload,{external_google_enabled:true,external_google_client_id:cfg.FASTCOLOC_GOOGLE_CLIENT_ID,external_google_secret:cfg.FASTCOLOC_GOOGLE_CLIENT_SECRET});
 const r=await fetch('https://api.supabase.com/v1/projects/nklsbubnyznlfpxpxsrp/config/auth',{method:'PATCH',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(payload)});
 if(!r.ok)throw new Error('Management API rejected configuration: HTTP '+r.status);
 console.log('FastColoc Auth configuration applied. Google changed only when both credentials were supplied.');
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
