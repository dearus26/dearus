/** SSE(text/event-stream)에서 읽어 낸 이벤트 하나. data는 JSON 글자 그대로예요. */
export type SseEvent = { event: string; data: string };

/**
 * 스트림에서 받은 글자 조각(chunk)을 이어 붙여서 이벤트 단위로 잘라 줘요.
 * 이벤트는 빈 줄로 끝나고, `event:` 줄이 이름, `data:` 줄이 내용이에요(여러 줄이면 줄바꿈으로 이어요).
 * `:`로 시작하는 줄은 연결 유지용 주석이라 버려요. 조각이 이벤트 중간에서 끊겨도 다음 조각과 이어서 읽어요.
 */
export function createSseParser(onEvent: (event: SseEvent) => void) {
  let buffer = '';

  function parseBlock(block: string) {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line === '' || line.startsWith(':')) continue;
      const colon = line.indexOf(':');
      const field = colon < 0 ? line : line.slice(0, colon);
      const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '');
      if (field === 'event') event = value;
      else if (field === 'data') data.push(value);
    }
    if (data.length > 0) onEvent({ event, data: data.join('\n') });
  }

  return {
    push(chunk: string) {
      // 끝이 \r에서 끊겼다가 다음 조각이 \n으로 시작해도 하나의 줄바꿈으로 읽히게, 쌓인 전체에서 바꿔요
      buffer = (buffer + chunk).replace(/\r\n/g, '\n');
      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        parseBlock(buffer.slice(0, boundary));
        buffer = buffer.slice(boundary + 2);
        boundary = buffer.indexOf('\n\n');
      }
    },
  };
}
