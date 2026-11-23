// Run from this directory: node setup.cjs. Secrets stay in ignored local files.
const fs=require('fs'),crypto=require('crypto'),cp=require('child_process'),path=require('path');
process.chdir(__dirname);
const envPath='.env.local';
function readEnv(){return Object.fromEntries(fs.readFileSync(envPath,'utf8').split(/\r?\n/).map(l=>l.match(/^([A-Z0-9_]+)=(.*)$/)).filter(Boolean).map(m=>[m[1],m[2]]));}
if(!fs.existsSync(envPath)){const env={NOTIFICATIONS_DB_PASSWORD:crypto.randomBytes(32).toString('hex'),N8N_ENCRYPTION_KEY:crypto.randomBytes(32).toString('hex'),NOTIFICATION_WEBHOOK_TOKEN:crypto.randomBytes(32).toString('hex'),N8N_OWNER_EMAIL:'admin@fastcoloc.test',N8N_OWNER_PASSWORD:'Aa1!'+crypto.randomBytes(24).toString('hex')};fs.writeFileSync(envPath,Object.entries(env).map(([k,v])=>k+'='+v).join('\n')+'\n',{mode:0o600});}
const env=readEnv();
const credentials=[{id:'fastcoloc-notification-hook',name:'FastColoc webhook authentication',type:'httpHeaderAuth',data:{name:'Authorization',value:'Bearer '+env.NOTIFICATION_WEBHOOK_TOKEN}},{id:'fastcoloc-notification-db',name:'FastColoc notification ledger',type:'postgres',data:{host:'db',database:'n8n',user:'fastcoloc',password:env.NOTIFICATIONS_DB_PASSWORD,port:5432,ssl:'disable'}},{id:'fastcoloc-local-smtp',name:'FastColoc local inbox',type:'smtp',data:{host:'mailpit',port:1025,secure:false,disableStartTls:true}}];
fs.writeFileSync('.env.credentials.json',JSON.stringify(credentials),{mode:0o600});
function docker(args,quiet=false){const r=cp.spawnSync('docker',['compose','--env-file',envPath,...args],{encoding:'utf8',stdio:quiet?'pipe':'inherit'});if(r.status!==0)throw Error('Docker Compose failed: '+args.slice(0,2).join(' '));return r.stdout??'';}
async function wait(){for(let i=0;i<90;i++){try{const r=await fetch('http://localhost:32768/healthz');if(r.ok)return;}catch{}await new Promise(r=>setTimeout(r,1000));}throw Error('n8n did not become healthy');}
(async()=>{
 docker(['up','-d']);await wait();
 let settings;for(let i=0;i<30;i++){try{const r=await fetch('http://localhost:32768/rest/settings');settings=await r.json();if(settings.data?.userManagement)break;}catch{}await new Promise(r=>setTimeout(r,1000));}if(!settings?.data?.userManagement)throw Error('n8n settings are not ready');
 if(settings.data?.userManagement?.showSetupOnFirstLoad){const r=await fetch('http://localhost:32768/rest/owner/setup',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:env.N8N_OWNER_EMAIL,firstName:'FastColoc',lastName:'Local',password:env.N8N_OWNER_PASSWORD})});if(!r.ok)throw Error('Local n8n owner setup HTTP '+r.status);console.log('Local n8n owner created; credentials are in infra/notifications/.env.local.');}
 const installed=docker(['exec','-T','db','psql','-U','fastcoloc','-d','n8n','-tAc',"SELECT count(*) FROM workflow_entity WHERE id='FastColocNotify01'"],true).trim()==='1';
 if(installed){console.log('Existing FastColoc notification workflow preserved. n8n: http://localhost:32768 | Inbox: http://localhost:8025');return;}
 docker(['exec','-T','n8n','n8n','import:credentials','--input=/seed/credentials.json']);
 docker(['exec','-T','n8n','n8n','import:workflow','--input=/seed/workflow.json']);
 docker(['exec','-T','n8n','n8n','publish:workflow','--id=FastColocNotify01']);
 docker(['restart','n8n']);await wait();
 console.log('Notification workflow installed. n8n: http://localhost:32768 | Local inbox: http://localhost:8025');
})().catch(e=>{console.error(e.message);process.exitCode=1;});
