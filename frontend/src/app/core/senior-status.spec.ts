import { SystemStatusComponentEnum, SystemStatusStateEnum } from '../api/model/models';
import { ConnectionState } from './events.service';
import { SeniorStatus, SystemStatusMap, selectSeniorStatus } from './senior-status';

type State = `${SystemStatusStateEnum}` | undefined;

const STATES: State[] = [undefined, 'ok', 'degraded', 'down'];
const CONNECTIONS: ConnectionState[] = ['open', 'connecting', 'closed'];

function statusMap(audio: State, stt: State, ai: State, backend: State = undefined): SystemStatusMap {
  const map: SystemStatusMap = {};
  const entries = [
    [SystemStatusComponentEnum.audio, audio],
    [SystemStatusComponentEnum.stt, stt],
    [SystemStatusComponentEnum.ai, ai],
    [SystemStatusComponentEnum.backend, backend],
  ] as const;
  for (const [component, state] of entries) {
    if (state) {
      map[component] = { component, state: state as SystemStatusStateEnum, message: 'x', at: '2026-10-04T10:00:00Z' };
    }
  }
  return map;
}

/** The priority from the task, written as rules checked in order. */
function expected(connection: ConnectionState, audio: State, stt: State, ai: State): SeniorStatus {
  if (connection !== 'open') return 'offline';
  if (audio === 'down' || stt === 'down') return 'not_hearing';
  if (audio === 'degraded') return 'paused';
  if (ai === 'degraded' || ai === 'down') return 'basic';
  return 'protected';
}

describe('selectSeniorStatus', () => {
  const call = { callId: 'c1', startedAt: '2026-10-04T10:00:00Z', endedAt: null, hadAlert: null };

  it('follows the priority for every combination of connection, audio, stt and ai', () => {
    let checked = 0;
    for (const connection of CONNECTIONS) {
      for (const audio of STATES) {
        for (const stt of STATES) {
          for (const ai of STATES) {
            const want = expected(connection, audio, stt, ai);
            const map = statusMap(audio, stt, ai);
            expect(selectSeniorStatus(connection, map, null), `${connection} a=${audio} s=${stt} ai=${ai}`).toBe(want);
            expect(selectSeniorStatus(connection, map, call)).toBe(want);
            checked++;
          }
        }
      }
    }
    expect(checked).toBe(3 * 4 * 4 * 4);
  });

  it('is offline when the backend reports itself down, even with an open socket', () => {
    expect(selectSeniorStatus('open', statusMap('ok', 'ok', 'ok', 'down'), null)).toBe('offline');
  });

  it('spot checks of the priority order', () => {
    expect(selectSeniorStatus('closed', statusMap('down', 'down', 'down'), null)).toBe('offline');
    expect(selectSeniorStatus('open', statusMap('ok', 'down', 'down'), null)).toBe('not_hearing');
    expect(selectSeniorStatus('open', statusMap('degraded', 'ok', 'down'), null)).toBe('paused');
    expect(selectSeniorStatus('open', statusMap('ok', 'ok', 'degraded'), null)).toBe('basic');
    expect(selectSeniorStatus('open', {}, null)).toBe('protected');
  });

  it('a local pause counts as audio degraded: below offline and not hearing, above basic and protected (FF-13)', () => {
    for (const connection of CONNECTIONS) {
      for (const audio of STATES) {
        for (const stt of STATES) {
          for (const ai of STATES) {
            const want = expected(connection, audio === 'down' ? 'down' : 'degraded', stt, ai);
            expect(selectSeniorStatus(connection, statusMap(audio, stt, ai), call, true)).toBe(want);
          }
        }
      }
    }
  });
});
