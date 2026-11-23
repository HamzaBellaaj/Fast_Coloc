// Default: validate the production webhook and replay the existing test (no new email).
// --send-test: send one real notification to N8N_TEST_RECIPIENT, then verify replay protection.
const fs=require('fs'),crypto=require('crypto'),path=require('path');process.chdir(__dirname);
const envPath='.env.cloud.local';
const env=Object.fromEntries(fs.readFileSync(envPath,'utf8').split(/\r?\n/).map(l=>l.match(/^([A-Z0-9_]+)=(.*)$/)).filter(Boolean).map(m=>[m[1],m[2].trim()]));
function save(values){let text=fs.readFileSync(envPath,'utf8');for(const [k,v]of Object.entries(values)){const re=new RegExp('^'+k+'=.*$','m');text=re.test(text)?text.replace(re,()=>k+'='+v):text.trimEnd()+'\n'+k+'='+v+'\n';}fs.writeFileSync(envPath,text);}
(async()=>{
 const base=new URL(env.N8N_CLOUD_BASE_URL);if(base.protocol!=='https:'||base.username||base.password)throw Error('Cloud webhook requires HTTPS');
 if(!env.N8N_CLOUD_WEBHOOK_TOKEN||!env.N8N_TEST_RECIPIENT)throw Error('Cloud webhook token and explicit test recipient required');
 const fresh=process.argv.includes('--send-test');
 const event={eventId:fresh?crypto.randomUUID():env.N8N_LAST_TEST_EVENT_ID,type:'NEW_MESSAGE',to:env.N8N_TEST_RECIPIENT,conversationId:fresh?crypto.randomUUID():env.N8N_LAST_TEST_CONVERSATION_ID};
 if(!event.eventId||!event.conversationId)throw Error('No previous test to replay; use --send-test with your approved recipient');
 async function post(body,auth){return fetch(new URL('/webhook/fastcoloc-message-emails',base),{method:'POST',headers:{'Content-Type':'application/json',...(auth?{Authorization:'Bearer '+env.N8N_CLOUD_WEBHOOK_TOKEN}:{})},body:JSON.stringify(body),signal:AbortSignal.timeout(20000),redirect:'error'});}
 const rejected=await post(event,false);if(![401,403].includes(rejected.status))throw Error('Unauthenticated webhook was not rejected: '+rejected.status);console.log('PASS: unauthenticated webhook rejected.');
 const invalid=await post({},true);if(invalid.status!==400)throw Error('Malformed event HTTP '+invalid.status);console.log('PASS: authenticated malformed event rejected.');
 if(fresh){save({N8N_LAST_TEST_EVENT_ID:event.eventId,N8N_LAST_TEST_CONVERSATION_ID:event.conversationId});const first=await post(event,true);const ack=await first.json();if(first.status!==200||ack.eventId!==event.eventId||ack.status!=='sent')throw Error('Gmail send not acknowledged: HTTP '+first.status);console.log('PASS: Gmail accepted a real email and the ledger recorded it.');}
 const repeat=await post(event,true);const ack=await repeat.json();if(repeat.status!==200||ack.eventId!==event.eventId||ack.status!=='duplicate')throw Error('Existing test was not acknowledged as duplicate: HTTP '+repeat.status);console.log('PASS: replay acknowledged as duplicate; no repeated email send.');
 save({N8N_CLOUD_WEBHOOK_VERIFIED_AT:new Date().toISOString()});
})().catch(error=>{console.error(error.message);process.exitCode=1;});
