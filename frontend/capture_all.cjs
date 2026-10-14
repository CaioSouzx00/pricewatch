const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

async function run() {
  const screenshotsDir = path.resolve(__dirname, '..', 'docs', 'screenshots');
  if (!fs.existsSync(screenshotsDir)) {
    fs.mkdirSync(screenshotsDir, { recursive: true });
  }

  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1.5,
  });
  const page = await context.newPage();

  console.log('1. Capturando Login...');
  await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(screenshotsDir, '01_login.png'), fullPage: false });

  console.log('Realizando login como admin@pricewatch.com...');
  await page.fill('input[type="email"]', 'admin@pricewatch.com');
  await page.fill('input[type="password"]', 'Password123!');
  await page.click('button[type="submit"]');

  await page.waitForURL('http://localhost:5173/', { timeout: 10000 });
  
  console.log('Aguardando carregamento dos dados do Dashboard...');
  await page.waitForFunction(() => !document.querySelector('.animate-spin') && !document.querySelector('svg.animate-spin'), { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(3000);

  console.log('2. Capturando Dashboard (Modo Claro)...');
  await page.screenshot({ path: path.join(screenshotsDir, '02_dashboard.png'), fullPage: false });

  console.log('3. Capturando Produtos...');
  await page.goto('http://localhost:5173/products', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(screenshotsDir, '03_products.png'), fullPage: false });

  console.log('4. Capturando Detalhes do Produto 101...');
  await page.goto('http://localhost:5173/products/101', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2500);
  await page.screenshot({ path: path.join(screenshotsDir, '04_product_detail.png'), fullPage: false });

  console.log('5. Capturando Alertas...');
  await page.goto('http://localhost:5173/alerts', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(screenshotsDir, '05_alerts.png'), fullPage: false });

  console.log('6. Capturando Watchlist...');
  await page.goto('http://localhost:5173/watchlist', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);
  await page.screenshot({ path: path.join(screenshotsDir, '06_watchlist.png'), fullPage: false });

  console.log('7. Capturando Dashboard no Modo Escuro...');
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);
  await page.evaluate(() => {
    document.documentElement.classList.add('dark');
    localStorage.setItem('pricewatch_theme', 'dark');
  });
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(screenshotsDir, '07_dark_mode_dashboard.png'), fullPage: false });

  await browser.close();
  console.log('Todas as capturas (incluindo tema escuro) foram concluídas com sucesso!');
}

run().catch((err) => {
  console.error('Erro na captura:', err);
  process.exit(1);
});
