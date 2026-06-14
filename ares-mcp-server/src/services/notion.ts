import fs from 'fs';
import path from 'path';

export function isNotionUrl(urlStr: string): boolean {
  try {
    const parsed = new URL(urlStr);
    return parsed.hostname.endsWith('notion.so') || parsed.hostname.endsWith('notion.site') || parsed.hostname.endsWith('notion.com');
  } catch {
    return false;
  }
}

export function extractPageId(urlStr: string): string {
  try {
    const parsed = new URL(urlStr);
    const pathParts = parsed.pathname.split('/');
    const lastPart = pathParts[pathParts.length - 1] || pathParts[pathParts.length - 2] || '';
    
    // Notion page IDs are 32 hex characters, potentially containing hyphens.
    // Strip hyphens and match the 32 hex characters anchored to the end of the last path segment.
    const cleanLastPart = lastPart.replace(/-/g, '');
    const match = cleanLastPart.match(/([0-9a-fA-F]{32})$/);
    if (match) {
      return match[1];
    }
  } catch (err) {
    // Fallback if URL parsing fails
  }

  const match = urlStr.replace(/-/g, '').match(/([0-9a-fA-F]{32})/);
  if (!match) {
    throw new Error(`Unable to extract a Notion page ID from URL: ${urlStr}`);
  }
  return match[1];
}

async function fetchPageTitle(pageId: string, token: string): Promise<string> {
  const response = await fetch(`https://api.notion.com/v1/pages/${pageId}`, {
    headers: {
      'Authorization': `Bearer ${token}`,
      'Notion-Version': '2022-06-28',
    },
  });
  if (!response.ok) {
    throw new Error(`Failed to fetch Notion page metadata: ${response.statusText} (${response.status})`);
  }
  const page = await response.json() as any;
  const properties = page.properties || {};
  for (const prop of Object.values(properties) as any[]) {
    if (prop.type === 'title') {
      const titleItems = prop.title || [];
      const title = titleItems.map((item: any) => item.plain_text || '').join('');
      if (title) return title;
    }
  }
  return page.id || 'Notion Document';
}

async function fetchBlockChildren(blockId: string, token: string): Promise<any[]> {
  const results: any[] = [];
  let startCursor: string | undefined = undefined;
  do {
    const url = new URL(`https://api.notion.com/v1/blocks/${blockId}/children`);
    url.searchParams.set('page_size', '100');
    if (startCursor) {
      url.searchParams.set('start_cursor', startCursor);
    }
    const response = await fetch(url.toString(), {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Notion-Version': '2022-06-28',
      },
    });
    if (!response.ok) {
      throw new Error(`Failed to fetch Notion block children: ${response.statusText} (${response.status})`);
    }
    const data = await response.json() as any;
    results.push(...(data.results || []));
    startCursor = data.has_more ? data.next_cursor : undefined;
  } while (startCursor);
  return results;
}

function richTextToPlain(items: any[]): string {
  return (items || []).map(item => item.plain_text || '').join('');
}

async function renderBlock(block: any, token: string): Promise<string[]> {
  const blockType = block.type;
  const blockData = block[blockType] || {};
  const lines: string[] = [];

  const richText = blockData.rich_text || [];
  const text = richTextToPlain(richText);

  if (blockType === 'paragraph') {
    if (text) lines.push(text);
  } else if (['heading_1', 'heading_2', 'heading_3'].includes(blockType)) {
    if (text) {
      const prefix = blockType === 'heading_1' ? '#' : blockType === 'heading_2' ? '##' : '###';
      lines.push(`${prefix} ${text}`);
    }
  } else if (['bulleted_list_item', 'numbered_list_item'].includes(blockType)) {
    if (text) {
      const bullet = blockType === 'bulleted_list_item' ? '-' : '1.';
      lines.push(`${bullet} ${text}`);
    }
  } else if (blockType === 'quote') {
    if (text) lines.push(`> ${text}`);
  } else if (blockType === 'to_do') {
    if (text) {
      const checked = blockData.checked ? '[x]' : '[ ]';
      lines.push(`- ${checked} ${text}`);
    }
  } else if (blockType === 'code') {
    if (text) {
      const language = blockData.language || '';
      lines.push(`\`\`\`${language}\n${text}\n\`\`\``);
    }
  } else if (blockType === 'child_page') {
    const title = blockData.title;
    if (title) lines.push(`[[Page: ${title}]]`);
  } else {
    if (text) lines.push(text);
  }

  if (block.has_children) {
    try {
      const children = await fetchBlockChildren(block.id, token);
      for (const child of children) {
        const childLines = await renderBlock(child, token);
        lines.push(...childLines);
      }
    } catch (err) {
      console.error(`[ARES-NOTION] Error rendering child block:`, err);
    }
  }

  return lines;
}

export async function resolveNotionTaskDescription(urlStr: string): Promise<string> {
  if (!isNotionUrl(urlStr)) {
    return urlStr;
  }

  let token = process.env.NOTION_TOKEN;
  if (!token) {
    try {
      const baseDir = process.env.ARES_WORKSPACE_PATH || process.cwd();
      const envPath = path.resolve(baseDir, '.env');
      if (fs.existsSync(envPath)) {
        const envContent = fs.readFileSync(envPath, 'utf8');
        const match = envContent.match(/^NOTION_TOKEN\s*=\s*(.+)$/m);
        if (match) {
          token = match[1].trim().replace(/^['"]|['"]$/g, '');
          process.env.NOTION_TOKEN = token;
        }
      }
    } catch (e) {
      // ignore
    }
  }

  if (!token) {
    throw new Error('Notion API token (NOTION_TOKEN) is not set in the environment.');
  }

  console.error(`[ARES-NOTION] Resolving task description from Notion page: ${urlStr}`);
  const pageId = extractPageId(urlStr);

  const title = await fetchPageTitle(pageId, token);
  const rootBlocks = await fetchBlockChildren(pageId, token);

  const renderedLines = [title];
  for (const block of rootBlocks) {
    const childLines = await renderBlock(block, token);
    renderedLines.push(...childLines);
  }

  return renderedLines.filter(line => line.trim()).join('\n\n');
}
