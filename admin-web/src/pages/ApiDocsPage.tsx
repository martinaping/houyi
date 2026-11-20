import { ApiOutlined, SearchOutlined } from '@ant-design/icons';
import { Card, Empty, Input, Tag, Typography, theme } from 'antd';
import type { ReactNode } from 'react';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import apiMarkdown from '../../../API.md?raw';
import { useAuth } from '../auth/AuthContext';

type Block =
  | { kind: 'heading'; level: number; text: string; id: string }
  | { kind: 'paragraph'; text: string }
  | { kind: 'code'; code: string }
  | { kind: 'table'; header: string[]; rows: string[][] }
  | { kind: 'list'; ordered: boolean; items: string[] }
  | { kind: 'quote'; text: string }
  | { kind: 'hr' };

type Section = {
  heading: Block | null;
  entries: { block: Block; index: number }[];
};

const INLINE_PATTERN = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g;

function splitRow(line: string): string[] {
  const trimmed = line.trim().replace(/^\|/, '').replace(/\|$/, '');
  const cells: string[] = [];
  let current = '';
  for (let index = 0; index < trimmed.length; index += 1) {
    const char = trimmed[index] ?? '';
    if (char === '\\' && trimmed[index + 1] === '|') {
      current += '|';
      index += 1;
      continue;
    }
    if (char === '|') {
      cells.push(current.trim());
      current = '';
      continue;
    }
    current += char;
  }
  cells.push(current.trim());
  return cells;
}

function parseMarkdown(source: string): Block[] {
  const lines = source.split(/\r?\n/);
  const blocks: Block[] = [];
  const paragraph: string[] = [];
  let headingSeq = 0;
  let index = 0;

  const flushParagraph = () => {
    if (paragraph.length > 0) {
      blocks.push({ kind: 'paragraph', text: paragraph.join(' ') });
      paragraph.length = 0;
    }
  };

  while (index < lines.length) {
    const line = lines[index] ?? '';
    const trimmed = line.trim();

    if (trimmed.startsWith('```')) {
      flushParagraph();
      const code: string[] = [];
      index += 1;
      while (index < lines.length && !(lines[index] ?? '').trim().startsWith('```')) {
        code.push(lines[index] ?? '');
        index += 1;
      }
      index += 1;
      blocks.push({ kind: 'code', code: code.join('\n') });
      continue;
    }

    const heading = /^(#{1,4})\s+(.*)$/.exec(trimmed);
    if (heading) {
      flushParagraph();
      headingSeq += 1;
      blocks.push({
        kind: 'heading',
        level: heading[1]?.length ?? 1,
        text: heading[2] ?? '',
        id: `api-section-${headingSeq}`,
      });
      index += 1;
      continue;
    }

    if (/^(-{3,}|\*{3,})$/.test(trimmed)) {
      flushParagraph();
      blocks.push({ kind: 'hr' });
      index += 1;
      continue;
    }

    if (trimmed.startsWith('>')) {
      flushParagraph();
      blocks.push({ kind: 'quote', text: trimmed.replace(/^>\s?/, '') });
      index += 1;
      continue;
    }

    if (trimmed.startsWith('|')) {
      const separator = (lines[index + 1] ?? '').trim();
      if (separator.includes('-') && /^\|[\s:|-]+\|?$/.test(separator)) {
        flushParagraph();
        const header = splitRow(trimmed);
        index += 2;
        const rows: string[][] = [];
        while (index < lines.length) {
          const rowLine = (lines[index] ?? '').trim();
          if (!rowLine.startsWith('|')) {
            break;
          }
          rows.push(splitRow(rowLine));
          index += 1;
        }
        blocks.push({ kind: 'table', header, rows });
        continue;
      }
    }

    const listItem = /^(-|\*|\d+\.)\s+(.*)$/.exec(trimmed);
    if (listItem) {
      flushParagraph();
      const ordered = /^\d+\.$/.test(listItem[1] ?? '');
      const items: string[] = [];
      while (index < lines.length) {
        const match = /^(-|\*|\d+\.)\s+(.*)$/.exec((lines[index] ?? '').trim());
        if (!match) {
          break;
        }
        items.push(match[2] ?? '');
        index += 1;
      }
      blocks.push({ kind: 'list', ordered, items });
      continue;
    }

    if (trimmed === '') {
      flushParagraph();
      index += 1;
      continue;
    }

    paragraph.push(trimmed);
    index += 1;
  }

  flushParagraph();
  return blocks;
}

function blockText(block: Block): string {
  switch (block.kind) {
    case 'heading':
    case 'paragraph':
    case 'quote':
      return block.text;
    case 'code':
      return block.code;
    case 'table':
      return [block.header, ...block.rows].flat().join(' ');
    case 'list':
      return block.items.join(' ');
    default:
      return '';
  }
}

/** 按二级标题切分章节，便于搜索时整节保留上下文。 */
function groupSections(blocks: Block[]): Section[] {
  const sections: Section[] = [];
  let current: Section = { heading: null, entries: [] };

  blocks.forEach((block, index) => {
    if (block.kind === 'heading' && block.level === 2) {
      if (current.heading || current.entries.length > 0) {
        sections.push(current);
      }
      current = { heading: block, entries: [{ block, index }] };
      return;
    }
    current.entries.push({ block, index });
  });

  if (current.heading || current.entries.length > 0) {
    sections.push(current);
  }
  return sections;
}

function emphasize(text: string, keyword: string, keyPrefix: string): ReactNode {
  if (!keyword) {
    return text;
  }
  const lowerText = text.toLowerCase();
  const lowerKeyword = keyword.toLowerCase();
  const parts: ReactNode[] = [];
  let cursor = 0;
  let ordinal = 0;
  let hit = lowerText.indexOf(lowerKeyword);

  while (hit >= 0) {
    if (hit > cursor) {
      parts.push(text.slice(cursor, hit));
    }
    parts.push(
      <mark key={`${keyPrefix}-mark-${ordinal}`} style={{ background: 'rgba(255, 197, 61, 0.5)', padding: 0 }}>
        {text.slice(hit, hit + keyword.length)}
      </mark>,
    );
    cursor = hit + keyword.length;
    ordinal += 1;
    hit = lowerText.indexOf(lowerKeyword, cursor);
  }

  if (cursor < text.length) {
    parts.push(text.slice(cursor));
  }
  return parts.length > 0 ? parts : text;
}

function renderInline(text: string, keyPrefix: string, keyword = ''): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = new RegExp(INLINE_PATTERN);
  let cursor = 0;
  let ordinal = 0;
  let match = pattern.exec(text);

  while (match) {
    if (match.index > cursor) {
      nodes.push(emphasize(text.slice(cursor, match.index), keyword, `${keyPrefix}-t${ordinal}`));
    }
    const token = match[0];
    const key = `${keyPrefix}-${ordinal}`;
    if (token.startsWith('**')) {
      nodes.push(
        <Typography.Text strong key={key}>
          {emphasize(token.slice(2, -2), keyword, `${keyPrefix}-b${ordinal}`)}
        </Typography.Text>,
      );
    } else if (token.startsWith('`')) {
      nodes.push(
        <Typography.Text code key={key}>
          {emphasize(token.slice(1, -1), keyword, `${keyPrefix}-c${ordinal}`)}
        </Typography.Text>,
      );
    } else {
      const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(token);
      nodes.push(
        <a key={key} href={link?.[2] ?? '#'} target="_blank" rel="noreferrer">
          {emphasize(link?.[1] ?? token, keyword, `${keyPrefix}-a${ordinal}`)}
        </a>,
      );
    }
    cursor = match.index + token.length;
    ordinal += 1;
    match = pattern.exec(text);
  }

  if (cursor < text.length) {
    nodes.push(emphasize(text.slice(cursor), keyword, `${keyPrefix}-tail`));
  }
  return nodes;
}

export function ApiDocsPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const { token } = theme.useToken();

  const blocks = useMemo(() => parseMarkdown(apiMarkdown), []);
  const sections = useMemo(() => groupSections(blocks), [blocks]);
  const [keyword, setKeyword] = useState('');
  const [activeId, setActiveId] = useState('');
  const trimmedKeyword = keyword.trim();
  const lowerKeyword = trimmedKeyword.toLowerCase();
  const matchedSections = useMemo(() => {
    if (!lowerKeyword) {
      return sections;
    }
    return sections.filter((section) =>
      section.entries.some(({ block }) => blockText(block).toLowerCase().includes(lowerKeyword)),
    );
  }, [sections, lowerKeyword]);
  const toc = useMemo(() => {
    const source = lowerKeyword
      ? matchedSections.flatMap((section) => section.entries.map((entry) => entry.block))
      : blocks;
    return source.filter(
      (block): block is Extract<Block, { kind: 'heading' }> =>
        block.kind === 'heading' && (block.level === 2 || block.level === 3),
    );
  }, [blocks, lowerKeyword, matchedSections]);

  const renderBlock = (block: Block, index: number): ReactNode => {
    switch (block.kind) {
      case 'heading': {
        if (block.level === 1) {
          return null;
        }
        const content = renderInline(block.text, `h-${index}`, trimmedKeyword);
        if (block.level === 2) {
          return (
            <Typography.Title
              key={block.id}
              id={block.id}
              level={4}
              style={{ marginTop: 32, marginBottom: 12, paddingBottom: 6, borderBottom: `1px solid ${token.colorBorderSecondary}` }}
            >
              {content}
            </Typography.Title>
          );
        }
        return (
          <Typography.Title key={block.id} id={block.id} level={5} style={{ marginTop: 20, marginBottom: 8 }}>
            {content}
          </Typography.Title>
        );
      }
      case 'paragraph':
        return (
          <Typography.Paragraph key={index} style={{ margin: '8px 0', lineHeight: 1.8 }}>
            {renderInline(block.text, `p-${index}`, trimmedKeyword)}
          </Typography.Paragraph>
        );
      case 'code':
        return (
          <pre
            key={index}
            style={{
              background: token.colorFillTertiary,
              border: `1px solid ${token.colorBorderSecondary}`,
              borderRadius: 6,
              padding: 12,
              overflowX: 'auto',
              fontSize: 12.5,
              lineHeight: 1.6,
              margin: '10px 0',
            }}
          >
            <code>{emphasize(block.code, trimmedKeyword, `code-${index}`)}</code>
          </pre>
        );
      case 'table': {
        const width = Math.max(block.header.length, ...block.rows.map((row) => row.length));
        return (
          <div key={index} style={{ overflowX: 'auto', margin: '10px 0' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead>
                <tr>
                  {Array.from({ length: width }, (_, cellIndex) => (
                    <th
                      key={cellIndex}
                      style={{
                        border: `1px solid ${token.colorBorderSecondary}`,
                        background: token.colorFillQuaternary,
                        padding: '6px 10px',
                        textAlign: 'left',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {renderInline(block.header[cellIndex] ?? '', `th-${index}-${cellIndex}`, trimmedKeyword)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {block.rows.map((row, rowIndex) => (
                  <tr key={rowIndex}>
                    {Array.from({ length: width }, (_, cellIndex) => (
                      <td
                        key={cellIndex}
                        style={{
                          border: `1px solid ${token.colorBorderSecondary}`,
                          padding: '6px 10px',
                          verticalAlign: 'top',
                        }}
                      >
                        {renderInline(row[cellIndex] ?? '', `td-${index}-${rowIndex}-${cellIndex}`, trimmedKeyword)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      }
      case 'list': {
        const items = block.items.map((item, itemIndex) => (
          <li key={itemIndex} style={{ margin: '4px 0' }}>
            {renderInline(item, `li-${index}-${itemIndex}`, trimmedKeyword)}
          </li>
        ));
        return block.ordered ? (
          <ol key={index} style={{ paddingLeft: 22, margin: '8px 0' }}>{items}</ol>
        ) : (
          <ul key={index} style={{ paddingLeft: 22, margin: '8px 0' }}>{items}</ul>
        );
      }
      case 'quote':
        return (
          <div
            key={index}
            style={{
              borderLeft: `3px solid ${token.colorPrimaryBorder}`,
              paddingLeft: 12,
              margin: '12px 0',
              color: token.colorTextSecondary,
            }}
          >
            {renderInline(block.text, `q-${index}`, trimmedKeyword)}
          </div>
        );
      case 'hr':
        return <div key={index} style={{ borderTop: `1px solid ${token.colorBorderSecondary}`, margin: '24px 0' }} />;
      default:
        return null;
    }
  };

  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0, marginBottom: 4 }}>
        <ApiOutlined /> {t('apiDocs.title')}{' '}
        <Tag color="blue">{user?.version ? `v${user.version}` : t('apiDocs.versionTag')}</Tag>
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
        {t('apiDocs.description')}
      </Typography.Paragraph>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder={t('apiDocs.searchPlaceholder')}
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setActiveId('');
          }}
          style={{ maxWidth: 360 }}
          aria-label={t('apiDocs.searchPlaceholder')}
        />
        {lowerKeyword ? (
          <Typography.Text type="secondary">
            {matchedSections.length > 0
              ? t('apiDocs.searchResult', { count: matchedSections.length })
              : t('apiDocs.searchEmpty')}
          </Typography.Text>
        ) : null}
      </div>
      <div style={{ display: 'flex', gap: 20, alignItems: 'flex-start' }}>
        <Card
          size="small"
          title={t('apiDocs.toc')}
          style={{
            flex: '0 0 260px',
            position: 'sticky',
            top: 8,
            maxHeight: 'calc(100vh - 140px)',
            overflow: 'auto',
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2, fontSize: 13 }}>
            {toc.length === 0 ? (
              <Typography.Text type="secondary">{t('apiDocs.searchEmpty')}</Typography.Text>
            ) : null}
            {toc.map((heading) => {
              const active = heading.id === activeId;
              return (
                <a
                  key={heading.id}
                  href={`#${heading.id}`}
                  aria-current={active ? 'location' : undefined}
                  style={{
                    padding: `2px 8px 2px ${heading.level === 3 ? 22 : 8}px`,
                    borderRadius: 4,
                    fontWeight: active ? 600 : 400,
                    background: active ? token.colorPrimaryBg : 'transparent',
                    color: active
                      ? token.colorPrimary
                      : heading.level === 3
                        ? token.colorTextSecondary
                        : token.colorText,
                    lineHeight: 1.7,
                  }}
                  onClick={(event) => {
                    event.preventDefault();
                    setActiveId(heading.id);
                    document.getElementById(heading.id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                  }}
                >
                  {heading.text}
                </a>
              );
            })}
          </div>
        </Card>
        <Card style={{ flex: 1, minWidth: 0 }} styles={{ body: { padding: '8px 20px 24px' } }}>
          {matchedSections.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('apiDocs.searchEmpty')} />
          ) : (
            <div style={{ fontSize: 14 }}>
              {matchedSections.map((section) =>
                section.entries.map(({ block, index }) => renderBlock(block, index)),
              )}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
