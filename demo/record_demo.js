// 함께타요 시연 영상 녹화 스크립트 (Playwright)
// 사용: 서버 실행 후  node record_demo.js http://localhost:8000 ./video
const { chromium } = require('playwright');
const BASE = process.argv[2] || 'http://localhost:8000';
const OUT = process.argv[3] || './video';
const wait = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1920, height: 1080 },
    recordVideo: { dir: OUT, size: { width: 1920, height: 1080 } } });
  const page = await ctx.newPage();
  const cap = (n, t, s) => page.evaluate(([n, t, s]) => cap(n, t, s), [n, t, s]);
  await page.goto(BASE + '/demo');
  const car = page.frameLocator('#car');
  const btn = (f, text) => f.locator('button', { hasText: text }).first();
  await wait(1500);
  await cap('01', '출발 전, 차 화면에 QR이 떠요', '동승자는 앱 설치 없이 폰 카메라로 찍어 탑승을 등록해요');
  const code = (await (await page.request.get(BASE + '/api/latest')).json()).code;
  await page.evaluate(c => { pa.src = '/p/' + c + '?slot=a'; pb.src = '/p/' + c + '?slot=b'; }, code);
  await wait(2500);
  const pa = page.frameLocator('#pa'), pb = page.frameLocator('#pb');
  await cap('02', '이름과 좌석을 고르면 탑승 완료', '차 화면의 좌석 배치도에 바로 반영돼요');
  await pa.locator('#name').pressSequentially('영희', { delay: 120 });
  await btn(pa, '뒷좌석 왼쪽').click(); await wait(500);
  await btn(pa, '탑승하기').click(); await wait(1200);
  await pb.locator('#name').pressSequentially('민수', { delay: 120 });
  await btn(pb, '조수석').click(); await wait(500);
  await btn(pb, '탑승하기').click(); await wait(1000);
  await page.request.post(BASE + `/api/trips/${code}/join`, { data: { name: '서준', seat: 'RR' } });
  await wait(2200);

  await cap('03', '여정 시작 · 주행 중에는 한 줄만', '운전자는 조작할 필요가 없고, 차가 구간별 거리와 연료를 기록해요');
  await btn(car, '여정 시작').click(); await wait(800);
  await btn(car, 'D').click(); await wait(1200);
  await btn(car, '+16km').click(); await wait(1600);
  await btn(car, '+16km').click(); await wait(2200);

  await cap('04', '정차 중 뒷좌석 왼쪽 문이 열렸어요', '차가 하차를 감지하고, 그 자리에 앉은 영희 님 폰으로 확인을 보내요');
  await btn(car, 'P').click(); await wait(1500);
  await btn(car, '뒷좌석 왼쪽').click(); await wait(3500);
  await btn(pa, '네, 내렸어요').click();
  await cap('05', '영희 님 구간 마감 · 남은 3명은 계속 주행', '중간에 내린 사람은 자신이 탄 구간만큼만 내요');
  await wait(2500);
  await btn(car, 'D').click(); await wait(1000);
  await btn(car, '+16km').click(); await wait(2200);

  await cap('06', '도착 후 P단 · 탄 만큼 자동 정산', '각자 폰에 금액과 계산 근거가 도착해요');
  await btn(car, 'P').click(); await wait(1200);
  await btn(car, '도착 · 정산하기').click(); await wait(3500);
  await btn(car, '정산 보내기').click(); await wait(4500);

  await cap('07', '전기차에서는 V2L 캠핑 플래너가 열려요', '귀가에 필요한 배터리는 남기고, 가전 사용 가능 시간을 계산해요');
  await btn(car, '새 여정 · 전기차').click(); await wait(1200);
  await btn(car, '여정 시작').click(); await wait(800);
  await btn(car, '도착 · 정산하기').click(); await wait(1200);
  await btn(car, '캠핑 모드').click(); await wait(2500);
  await car.locator('#apps > div').nth(4).click(); await wait(2500);
  await car.locator('#apps > div').nth(4).click(); await wait(2000);
  await cap('', '함께타요', '함께 타고, 공정하게 나눠요');
  await wait(2500);
  await ctx.close(); await browser.close();
})();
