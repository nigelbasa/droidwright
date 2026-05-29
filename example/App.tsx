import Droidwright, { DroidwrightPage, NativeDroidwright } from 'droidwright';
import { useEvent } from 'expo';
import { useEffect, useState } from 'react';
import { Button, SafeAreaView, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';

declare global {
  interface Window {
    visibleClicked?: boolean;
    delayedClicked?: boolean;
    coveredClicked?: boolean;
  }
}

const fixtureHtml = `
<!doctype html>
<html>
  <head>
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <style>
      body { font-family: sans-serif; min-height: 2200px; padding: 24px; }
      button, input { display: block; margin: 16px 0; min-height: 44px; }
      #hidden { display: none; }
      #covered-target { position: relative; width: 220px; height: 56px; }
      #cover {
        position: absolute;
        left: 24px;
        top: 340px;
        width: 260px;
        height: 80px;
        background: rgba(0, 0, 0, 0.2);
        z-index: 2;
      }
      #covered-button { position: absolute; left: 24px; top: 340px; width: 220px; height: 56px; }
      #delayed { display: none; margin-top: 720px; }
    </style>
  </head>
  <body>
    <h1>Droidwright Fixture</h1>
    <button id="visible" onclick="window.visibleClicked = true">Visible action</button>
    <input id="name" />
    <button id="hidden" onclick="window.hiddenClicked = true">Hidden action</button>
    <button id="disabled" disabled>Disabled action</button>
    <button id="covered-button" onclick="window.coveredClicked = true">Covered action</button>
    <div id="cover"></div>
    <button id="delayed" onclick="window.delayedClicked = true">Delayed action</button>
    <script>
      setTimeout(function () {
        document.querySelector('#delayed').style.display = 'block';
      }, 350);
    </script>
  </body>
</html>
`;

export default function App() {
  const [log, setLog] = useState<string[]>([]);
  const [humanPace, setHumanPace] = useState(true);
  const event = useEvent(NativeDroidwright, 'onEvent');

  useEffect(() => {
    if (event) {
      setLog((items) =>
        [`${event.type}: ${event.url ?? event.message ?? ''}`, ...items].slice(0, 10)
      );
    }
  }, [event]);

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Droidwright</Text>
        <Group name="Interaction Profile">
          <View style={styles.row}>
            <Text>Human-paced actions</Text>
            <Switch value={humanPace} onValueChange={setHumanPace} />
          </View>
        </Group>
        <Group name="Actionability Smoke">
          <Button
            title="Run smoke script"
            onPress={async () => {
              const results = await runActionabilitySmoke();
              setLog((items) => [...results, ...items].slice(0, 14));
            }}
          />
        </Group>
        <Group name="Books to Scrape">
          <Button
            title="Run public-site smoke"
            onPress={async () => {
              const results = await runBooksSmoke(humanPace);
              setLog((items) => [...results, ...items].slice(0, 18));
            }}
          />
        </Group>
        <Group name="Events">
          {log.map((line, index) => (
            <Text key={`${line}-${index}`}>{line}</Text>
          ))}
        </Group>
      </ScrollView>
    </SafeAreaView>
  );
}

async function runBooksSmoke(humanPace: boolean) {
  const page = await Droidwright.launch({
    debug: true,
    forwardConsole: true,
    viewportWidth: 390,
    viewportHeight: 844,
  });
  const actionOptions = {
    timeoutMs: 10000,
    humanPace: humanPace
      ? {
          enabled: true,
          movementSteps: 7,
          minDelayMs: 120,
          maxDelayMs: 320,
          postActionDelayMs: 240,
        }
      : false,
  };

  try {
    await page.goto('https://books.toscrape.com/', { timeoutMs: 20000 });
    await page.waitForSelector('.product_pod', { timeoutMs: 15000 });
    const firstPage = await readBooks(page);
    await page.scrollIntoView('.next a');
    await page.click('.next a', actionOptions);
    await waitForPageTwo(page, firstPage[0]?.title ?? '', 15000);
    const secondPage = await readBooks(page);

    return [
      `humanPace: ${humanPace ? 'on' : 'off'}`,
      `page1: ${firstPage.length} books`,
      `page1 first: ${firstPage[0]?.title ?? 'none'} (${firstPage[0]?.price ?? '-'})`,
      `page2: ${secondPage.length} books`,
      `page2 first: ${secondPage[0]?.title ?? 'none'} (${secondPage[0]?.price ?? '-'})`,
    ];
  } finally {
    await page.close();
  }
}

async function readBooks(page: DroidwrightPage) {
  const books = await page.evaluate<Array<{ title: string; price: string }>>(`(function() {
    return Array.from(document.querySelectorAll('.product_pod'))
      .slice(0, 20)
      .map(function(book) {
        return {
          title: (book.querySelector('h3 a') && book.querySelector('h3 a').getAttribute('title')) || '',
          price: (book.querySelector('.price_color') && book.querySelector('.price_color').textContent.trim()) || ''
        };
      });
  })()`);

  return books ?? [];
}

async function waitForPageTwo(page: DroidwrightPage, previousFirstTitle: string, timeoutMs: number) {
  const start = Date.now();
  const previousTitleJson = JSON.stringify(previousFirstTitle);

  while (Date.now() - start < timeoutMs) {
    const isReady = await page.evaluate<boolean>(`(function() {
      var first = document.querySelector('h3 a');
      var title = first ? first.getAttribute('title') : '';
      return location.href.indexOf('page-2.html') !== -1 && title && title !== ${previousTitleJson};
    })()`);

    if (isReady) {
      return;
    }

    await delay(250);
  }

  throw new Error('Timed out waiting for Books to Scrape page 2 content');
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function runActionabilitySmoke() {
  const page = await Droidwright.launch({ debug: true, forwardConsole: true });
  const results: string[] = [];

  try {
    await page.goto(`data:text/html;charset=utf-8,${encodeURIComponent(fixtureHtml)}`);
    await page.click('#visible');
    await page.fill('#name', 'Ada Lovelace');
    await page.getByText('Delayed action', { exact: true }).click({ timeoutMs: 2000 });

    results.push(`visible: ${await page.evaluate('Boolean(window.visibleClicked)')}`);
    results.push(
      `name: ${await page.evaluate("(document.querySelector('#name') || {}).value")}`
    );
    results.push(`delayed: ${await page.evaluate('Boolean(window.delayedClicked)')}`);

    results.push(await expectActionError(page, '#hidden', 'ERR_DROIDWRIGHT_NOT_VISIBLE'));
    results.push(await expectActionError(page, '#disabled', 'ERR_DROIDWRIGHT_NOT_ENABLED'));
    results.push(
      await expectActionError(page, '#covered-button', 'ERR_DROIDWRIGHT_ELEMENT_COVERED')
    );

    await page.click('#covered-button', { force: true });
    results.push(`force: ${await page.evaluate('Boolean(window.coveredClicked)')}`);
  } finally {
    await page.close();
  }

  return results;
}

async function expectActionError(page: DroidwrightPage, selector: string, code: string) {
  try {
    await page.click(selector, { timeoutMs: 500 });
    return `${selector}: missing expected ${code}`;
  } catch (error) {
    return `${selector}: ${readErrorCode(error) === code ? code : `got ${readErrorCode(error)}`}`;
  }
}

function readErrorCode(error: unknown) {
  if (error && typeof error === 'object' && 'code' in error) {
    return String((error as { code?: string }).code);
  }
  return 'unknown';
}

function Group(props: { name: string; children: React.ReactNode }) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupHeader}>{props.name}</Text>
      {props.children}
    </View>
  );
}

const styles = StyleSheet.create({
  header: { fontSize: 30, margin: 20 },
  groupHeader: { fontSize: 20, marginBottom: 20 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  group: { margin: 20, backgroundColor: '#fff', borderRadius: 10, padding: 20 },
  container: { flex: 1, backgroundColor: '#eee' },
});
