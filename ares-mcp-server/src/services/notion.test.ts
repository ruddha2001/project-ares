import { describe, test, expect, mock, beforeEach, afterEach } from "bun:test";
import { isNotionUrl, extractPageId, resolveNotionTaskDescription } from "./notion";

describe("Notion Service Utilities", () => {
  test("isNotionUrl identifies valid Notion page URLs", () => {
    expect(isNotionUrl("https://www.notion.so/myworkspace/My-Page-Title-a1b2c3d4e5f67890a1b2c3d4e5f67890")).toBe(true);
    expect(isNotionUrl("https://notion.site/a1b2c3d4e5f67890a1b2c3d4e5f67890")).toBe(true);
    expect(isNotionUrl("https://google.com")).toBe(false);
    expect(isNotionUrl("not-a-url")).toBe(false);
  });

  test("extractPageId parses page UUID/ID from URL", () => {
    const hexId = "a1b2c3d4e5f67890a1b2c3d4e5f67890";
    expect(extractPageId(`https://www.notion.so/myworkspace/My-Page-${hexId}`)).toBe(hexId);
    expect(extractPageId(`https://notion.site/${hexId}`)).toBe(hexId);
    expect(extractPageId(`https://www.notion.so/myworkspace/My-Page-a1b2c3d4-e5f6-7890-a1b2-c3d4e5f67890`)).toBe(hexId);
  });

  test("resolveNotionTaskDescription returns original text if not a Notion URL", async () => {
    const description = "Implement login using OAuth2 client credentials grant flow.";
    const result = await resolveNotionTaskDescription(description);
    expect(result).toBe(description);
  });
});

describe("resolveNotionTaskDescription with Mock API", () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    process.env.NOTION_TOKEN = "test-token";
  });

  afterEach(() => {
    global.fetch = originalFetch;
    delete process.env.NOTION_TOKEN;
  });

  test("resolves page and formats blocks into markdown", async () => {
    global.fetch = mock((input: any) => {
      const url = String(input);
      if (url.includes("/v1/pages/")) {
        return Promise.resolve(new Response(JSON.stringify({
          id: "a1b2c3d4e5f67890a1b2c3d4e5f67890",
          properties: {
            Name: {
              type: "title",
              title: [{ plain_text: "Mocked Page Title" }]
            }
          }
        }), { status: 200 }));
      }
      if (url.includes("/children")) {
        return Promise.resolve(new Response(JSON.stringify({
          results: [
            {
              id: "block-1",
              type: "heading_1",
              has_children: false,
              heading_1: {
                rich_text: [{ plain_text: "Introduction Section" }]
              }
            },
            {
              id: "block-2",
              type: "paragraph",
              has_children: false,
              paragraph: {
                rich_text: [{ plain_text: "This is a body paragraph text." }]
              }
            },
            {
              id: "block-3",
              type: "bulleted_list_item",
              has_children: false,
              bulleted_list_item: {
                rich_text: [{ plain_text: "Bullet point one" }]
              }
            }
          ],
          has_more: false
        }), { status: 200 }));
      }
      return Promise.reject(new Error("Unexpected request: " + url));
    }) as any;

    const result = await resolveNotionTaskDescription("https://notion.so/my-workspace/Mocked-Page-a1b2c3d4e5f67890a1b2c3d4e5f67890");
    const expected = "Mocked Page Title\n\n# Introduction Section\n\nThis is a body paragraph text.\n\n- Bullet point one";
    expect(result).toBe(expected);
  });
});
