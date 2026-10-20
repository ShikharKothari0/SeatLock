import { chromium } from 'playwright';
import { spawn } from 'node:child_process';
import { mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';

const base = 'http://127.0.0.1:4186';
const server = spawn(process.execPath, ['node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '4186', '--strictPort'], { windowsHide: true, stdio: 'pipe' });
let browser;
try {
  for (let i=0;i<60;i++) { try { if ((await fetch(base)).ok) break; } catch {} await new Promise(r=>setTimeout(r,500)); }
  browser = await chromium.launch(process.env.PLAYWRIGHT_CHANNEL ? {channel:process.env.PLAYWRIGHT_CHANNEL} : {});
  const page = await browser.newPage();
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  let venues=[], postedVenue, postedEvent, failVenue=true, reopened=false;
  const future=new Date(Date.now()+86400000).toISOString();
  await page.route('**/api/**',async route=>{
    const req=route.request(), path=new URL(req.url()).pathname;
    let data={},status=200;
    if(path==='/api/auth/me') data={id:'admin',role:'ADMIN',displayName:'Admin'};
    else if(path==='/api/auth/csrf') data={headerName:'X-CSRF-TOKEN',token:'test'};
    else if(path==='/api/seats/holds') data={holds:[],serverTime:new Date().toISOString()};
    else if(path==='/api/admin/catalog/venues' && req.method()==='POST') {
      postedVenue=req.postDataJSON();
      if(failVenue) {status=400;data={message:'Please retry this venue.'};}
      else { venues=[{id:'venue',name:postedVenue.name,city:'Mumbai',layout_seats:20}];data={id:'venue'}; }
    } else if(path==='/api/admin/catalog/venues') data=venues;
    else if(path.endsWith('/sections')) data=[{section:'Standard',seats:20}];
    else if(path.endsWith('/reopen')) {reopened=true;}
    else if(path==='/api/admin/catalog/events' && req.method()==='POST') {postedEvent=req.postDataJSON();data={id:'draft'};}
    else if(path==='/api/admin/catalog/events') data=[
      {id:'closed',name:'Future event',publication:reopened?'PUBLISHED':'CLOSED',archived:false,event_time:future,sale_end_time:future,prices:[],fee_minor:0},
      {id:'archived',name:'Archived event',publication:'CLOSED',archived:true,event_time:future,sale_end_time:future,prices:[],fee_minor:0},
      {id:'past',name:'Past event',publication:'CLOSED',archived:false,event_time:'2020-01-01',sale_end_time:'2020-01-01',prices:[],fee_minor:0}];
    else if(path==='/api/events') {await new Promise(r=>setTimeout(r,2500));data=[];}
    await route.fulfill({status,contentType:'application/json',body:JSON.stringify(data)});
  });
  await page.goto(base+'/login');
  await page.getByLabel('Password',{exact:true}).fill('example-secret');
  await page.getByRole('button',{name:'Show password',exact:true}).click();
  assert.equal(await page.getByLabel('Password',{exact:true}).getAttribute('type'),'text');
  assert.equal(await page.getByLabel('Password',{exact:true}).inputValue(),'example-secret');
  await page.getByRole('button',{name:'Hide password',exact:true}).click();
  assert.equal(await page.getByLabel('Password',{exact:true}).getAttribute('type'),'password');
  await mkdir('test-results',{recursive:true});
  await page.screenshot({path:'test-results/polish-login.png'});
  await page.goto(base+'/');
  const spinner=page.getByRole('status',{name:'Loading events'});await spinner.waitFor();
  assert.equal(await spinner.evaluate(el=>getComputedStyle(el).borderTopWidth),'0px');
  await page.screenshot({path:'test-results/polish-spinner.png'});
  await page.goto(base+'/admin/catalog');
  await page.getByLabel('Seats per row',{exact:true}).fill('6');
  await page.getByLabel('Centre aisle',{exact:true}).check();
  await page.getByRole('button',{name:'Add section',exact:true}).click();
  await page.getByLabel('Section name',{exact:true}).nth(1).fill('Premium');
  await page.getByLabel('Seat prefix',{exact:true}).nth(1).fill('P');
  await page.getByRole('button',{name:'Move section 2 forward',exact:true}).click();
  assert.equal(await page.getByLabel('Section name',{exact:true}).first().inputValue(),'Premium');
  await page.getByRole('button',{name:'Move section 1 back',exact:true}).click();
  assert.equal(await page.getByLabel('Seats per row',{exact:true}).first().inputValue(),'6');
  await page.screenshot({path:'test-results/section-venue-preview.png',fullPage:true});
  await page.getByRole('button',{name:'Delete section 2',exact:true}).click();
  assert.equal(await page.getByRole('button',{name:'Delete section 1',exact:true}).isDisabled(),true);
  await page.getByLabel('Venue name',{exact:true}).fill('Demo hall');
  await page.getByLabel('City',{exact:true}).fill('Mumbai');
  await page.getByLabel('Address',{exact:true}).fill('Demo street');
  await page.getByRole('button',{name:'Create venue',exact:true}).click();
  await page.getByRole('alert').filter({hasText:'Please retry'}).waitFor();
  assert.equal(await page.getByLabel('Venue name',{exact:true}).inputValue(),'Demo hall');
  failVenue=false;
  await page.getByRole('button',{name:'Create venue',exact:true}).click();
  await page.getByText('Venue and reusable seating layout created.').waitFor();
  assert.equal(await page.getByLabel('Venue name',{exact:true}).inputValue(),'');
  assert.equal(postedVenue.sections.length,1);
  assert.equal(postedVenue.sections[0].seatsPerRow,6);
  assert.equal(postedVenue.sections[0].centreAisle,true);
  await page.getByLabel('Event name',{exact:true}).fill('Draft');
  await page.getByLabel('Sales start',{exact:true}).fill('2030-01-01T12:00');
  await page.getByLabel('Sales close',{exact:true}).fill('2030-01-02T12:00');
  await page.getByLabel('Event time',{exact:true}).fill('2030-01-02T13:00');
  await page.getByLabel(/Standard price/).fill('500');
  await page.getByRole('button',{name:'Create draft event',exact:true}).click();
  await page.getByText('Draft event created.',{exact:false}).waitFor();
  assert.equal(postedEvent.name,'Draft');
  assert.equal(await page.getByLabel('Event name',{exact:true}).inputValue(),'');
  assert.equal(await page.getByLabel('Venue',{exact:true}).inputValue(),'');
  assert.equal(await page.getByRole('button',{name:'Reopen sales',exact:true}).count(),1);
  page.once('dialog',d=>d.accept());
  await page.getByRole('button',{name:'Reopen sales',exact:true}).click();
  await page.getByText('Sales reopened.',{exact:false}).waitFor();assert.equal(reopened,true);
  await page.screenshot({path:'test-results/polish-catalog.png',fullPage:true});
  assert.deepEqual(errors,[]);
  console.log('PASS: password visibility, borderless spinner, section removal, failure preservation, successful form resets, and reopening controls (API fixtures).');
} finally {await browser?.close();server.kill();}
