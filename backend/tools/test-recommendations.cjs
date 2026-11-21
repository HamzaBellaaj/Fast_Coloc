/** One temporary account, one actual AI call, no changes to existing accounts/listings. */
const {randomUUID} = require('node:crypto');
const {writeFileSync} = require('node:fs');
const {join} = require('node:path');
const {operator,api} = require('./sprint2-fixtures.cjs');

(async () => {
  let user;
  const report = {};
  try {
    const denied = await fetch('http://127.0.0.1:8001/api/v1/recommendations/ai',{method:'POST'});
    if (denied.status !== 401) throw Error('Endpoint must require authentication');
    report.unauthenticatedStatus = denied.status;
    const email = `fastcoloc-us26-${randomUUID()}@example.com`;
    const password = 'Fc!aA9' + randomUUID();
    user = await operator('POST','/auth/v1/admin/users',{email,password,email_confirm:true});
    if (!user.id) throw Error('Fixture account creation failed');
    const session = await api('POST','/auth/login',null,{email,password});
    await api('POST','/users/sync',session.access_token,{});
    const areas = await api('GET','/listings/areas');
    const area = areas.find(item => item.locality.toLowerCase().includes('ariana')) || areas[0];
    await api('PUT','/users/me/preferences',session.access_token,{minBudget:250,maxBudget:900,moveInDate:null,localityIds:area ? [area.id] : []});
    const started = Date.now();
    const result = await api('POST','/recommendations/ai',session.access_token);
    report.elapsedSeconds = Math.round((Date.now()-started)/1000);
    if (result.success !== true || !Array.isArray(result.recommendations)) throw Error('Invalid response');
    const seen = new Set();
    for (const item of result.recommendations) {
      const row = item.listing;
      if (seen.has(row.id) || row.author_id === user.id || row.kind !== 'OFFER' || row.status !== 'PUBLISHED' || row.is_hidden || Date.parse(row.expires_at) <= Date.now()) throw Error('Unauthorized or duplicate listing');
      if (!Number.isInteger(item.score) || item.score < 0 || item.score > 100 || !item.raison) throw Error('Invalid score/reason');
      seen.add(row.id);
    }
    if (seen.size > 10) throw Error('More than ten recommendations');
    report.recommendations = result.recommendations.map(item => ({id:item.listing.id,score:item.score}));
    report.profileIncomplete = result.profileIncomplete;
    report.success = true;
  } finally {
    if (user?.id) {
      await operator('DELETE','/auth/v1/admin/users/'+user.id);
      report.fixtureRemoved = true;
    }
    writeFileSync(join(__dirname,'../target/recommendations-live-report.json'),JSON.stringify(report,null,2));
  }
  console.log(JSON.stringify(report));
})().catch(error => { console.error('US26 verification failed:',error.message); process.exitCode=1; });
