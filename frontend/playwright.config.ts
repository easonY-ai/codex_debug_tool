import { defineConfig, devices } from '@playwright/test'
import path from 'node:path'

const mavenRepo = process.env.TRACE_LENS_MAVEN_REPO ?? '/tmp/trace-lens-e2e-m2'
const fixtureRoot = path.resolve('e2e/fixtures/codex')
const database = `/tmp/trace-lens-e2e-${process.pid}.sqlite`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: { baseURL: 'http://127.0.0.1:4173', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    { command: `mvn -B -ntp -Dmaven.repo.local=${mavenRepo} -f ../backend/pom.xml -Dspring-boot.run.arguments="--analyzer.database=${database} --analyzer.jsonl.enabled=true --analyzer.jsonl.root=${fixtureRoot}" spring-boot:run`, url:'http://127.0.0.1:8080/api/ingestion/status', reuseExistingServer:true, timeout:60_000 },
    { command:'npm run dev -- --port 4173',url:'http://127.0.0.1:4173',reuseExistingServer:true,timeout:30_000 },
  ],
})
