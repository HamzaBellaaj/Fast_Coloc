const fs=require('fs'),crypto=require('crypto');process.chdir(__dirname);
const env=Object.fromEntries(fs.readFileSync('.env.local','utf8').split(/\r?\n/).map(l=>l.match(/^([A-Z0-9_]+)=(.*)$/)).filter(Boolean).map(m=>[m[1],m[2]]));
async function post(body,authenticated=true){return fetch('http://localhost:32768/webhook/fastcoloc-notifications',{method:'POST',headers:{'Content-Type':'application/json',...(authenticated?{Authorization:'Bearer '+env.NOTIFICATION_WEBHOOK_TOKEN}:{})},body:JSON.stringify(body),signal:AbortSignal.timeout(25000)});}
(async()=>{
 const event={eventId:crypto.randomUUID(),type:'NEW_MESSAGE',to:'workflow-test.'+crypto.randomUUID()+'@example.invalid',conversationId:crypto.randomUUID()};
 const unauthorized=await post(event,false);if(unauthorized.status!==401&&unauthorized.status!==403)throw Error('Unauthenticated webhook was not rejected: '+unauthorized.status);console.log('PASS: unauthenticated webhook rejected.');
 const invalid=await post({});if(invalid.status!==400)throw Error('Malformed event status '+invalid.status);console.log('PASS: malformed event rejected.');
 const before=await fetch('http://localhost:8025/api/v1/messages').then(r=>r.json());const countBefore=before.messages.filter(m=>m.To?.some(t=>t.Address===event.to)).length;
 const first=await post(event);if(first.status!==200){console.error('Webhook HTTP '+first.status);throw Error('First delivery failed');}const ack=await first.json();if(ack.eventId!==event.eventId||ack.status!=='sent')throw Error('Invalid email acknowledgement');
 const repeat=await post(event);const repeated=await repeat.json();if(repeat.status!==200||repeated.status!=='duplicate')throw Error('Duplicate event not suppressed');
 const inbox=await fetch('http://localhost:8025/api/v1/messages').then(r=>r.json());if(inbox.messages.filter(m=>m.To?.some(t=>t.Address===event.to)).length!==countBefore+1)throw Error('Duplicate SMTP delivery detected');const sample=inbox.messages.find(m=>m.To?.some(t=>t.Address===event.to));if(!sample)throw Error('Email not found in the local inbox');const message=await fetch('http://localhost:8025/api/v1/message/'+sample.ID).then(r=>r.json());if(!message.Text.includes(event.conversationId))throw Error('Email is missing discussion link');
 console.log('PASS: n8n delivered email to the local inbox and acknowledged it.');console.log('PASS: duplicate event did not produce another SMTP send.');
 if(process.argv.includes('--smtp-retry')){
  const endpoint='http://localhost:8025/api/v1/chaos';const current=await fetch(endpoint);if(!current.ok)throw Error('Local inbox chaos testing is not enabled');const previous=await current.json();
  const configure=async settings=>{let r=await fetch(endpoint,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(settings)});if(r.status===405)r=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(settings)});if(!r.ok)throw Error('Chaos configuration HTTP '+r.status);};
  const retryEvent={...event,eventId:crypto.randomUUID(),to:'smtp-retry.'+crypto.randomUUID()+'@example.invalid'};
  try{await configure({...previous,Sender:{ErrorCode:451,Probability:100}});const failure=await post(retryEvent);if(failure.status!==503)throw Error('Expected retryable SMTP failure, received '+failure.status);}finally{await configure(previous);}
  const retry=await post(retryEvent);const recovered=await retry.json();if(retry.status!==200||recovered.status!=='sent')throw Error('SMTP retry did not recover');console.log('PASS: SMTP rejection returned a retryable failure; the same event delivered after recovery.');
 }
 console.log('Sample notification available at http://localhost:8025');
})().catch(e=>{console.error(e.message);process.exitCode=1;});
