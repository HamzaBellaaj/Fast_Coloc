// Explicit selection: default local inbox, or --cloud after cloud smoke tests pass.
const fs=require('fs'),path=require('path');
const directory=__dirname,backend=path.resolve(directory,'../../backend/.env.local'),cloud=process.argv.includes('--cloud');
function parse(p){return Object.fromEntries(fs.readFileSync(p,'utf8').split(/\r?\n/).map(l=>l.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/)).filter(Boolean).map(m=>[m[1],m[2].trim()]));}
const infra=parse(path.join(directory,cloud?'.env.cloud.local':'.env.local')),previous=parse(backend),localUrl='http://localhost:32768/webhook/fastcoloc-notifications';
const url=cloud?new URL('/webhook/fastcoloc-message-emails',infra.N8N_CLOUD_BASE_URL).href:localUrl;
if(cloud&&(!url.startsWith('https://')||!infra.N8N_CLOUD_WEBHOOK_VERIFIED_AT))throw Error('Run test-cloud.cjs successfully against the HTTPS production webhook first.');
if(previous.FASTCOLOC_NOTIFICATION_WEBHOOK_URL&&![url,...(cloud?[localUrl]:[])].includes(previous.FASTCOLOC_NOTIFICATION_WEBHOOK_URL))throw Error('Existing notification endpoint preserved; review it before switching.');
const token=cloud?infra.N8N_CLOUD_WEBHOOK_TOKEN:infra.NOTIFICATION_WEBHOOK_TOKEN;if(!token)throw Error('Notification webhook token is missing.');
let text=fs.readFileSync(backend,'utf8');
for(const [key,value]of Object.entries({FASTCOLOC_NOTIFICATIONS_ENABLED:'true',FASTCOLOC_NOTIFICATION_WEBHOOK_URL:url,FASTCOLOC_NOTIFICATION_WEBHOOK_TOKEN:token,FASTCOLOC_NOTIFICATION_MODE:cloud?'external':'local'})){
 const regex=new RegExp('^'+key+'=.*$','m');text=regex.test(text)?text.replace(regex,()=>key+'='+value):text.trimEnd()+'\n'+key+'='+value+'\n';
}
fs.writeFileSync(backend,text,'utf8');console.log('Backend configured for '+(cloud?'Gmail Cloud':'the local inbox')+'. Restart the backend to load it.');
