import { SystemStatus, SystemStatusComponentEnum, SystemStatusStateEnum } from '../api/model/models';
import { ActiveCall, ConnectionState } from './events.service';

/** Resting state of the senior screen, most important first (FE-06). Never green on a failure. */
export type SeniorStatus = 'offline' | 'not_hearing' | 'paused' | 'basic' | 'protected';

export type SystemStatusMap = Partial<Record<SystemStatusComponentEnum, SystemStatus>>;

const { down, degraded } = SystemStatusStateEnum;

/**
 * offline > not hearing (audio or stt down) > paused (audio degraded) > basic protection (ai degraded or down)
 * > protected. A component without a published status counts as working.
 *
 * `activeCall` is part of the signature for callers; the priority itself does not depend on it.
 */
export function selectSeniorStatus(
  connection: ConnectionState,
  systemStatus: SystemStatusMap,
  activeCall: ActiveCall | null,
): SeniorStatus {
  const state = (component: SystemStatusComponentEnum) => systemStatus[component]?.state;

  if (connection !== 'open' || state(SystemStatusComponentEnum.backend) === down) return 'offline';
  if (state(SystemStatusComponentEnum.audio) === down || state(SystemStatusComponentEnum.stt) === down) {
    return 'not_hearing';
  }
  if (state(SystemStatusComponentEnum.audio) === degraded) return 'paused';
  const ai = state(SystemStatusComponentEnum.ai);
  if (ai === degraded || ai === down) return 'basic';
  return 'protected';
}
