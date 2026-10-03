import { SeniorStatus } from '../core/senior-status';
import { IconName } from './icon';

/** One sentence, icon and colour per protection state; shared by the senior screen and the family status bar. */
export type StatusTone = 'green' | 'yellow' | 'red';

export const STATUS_VIEW: Record<SeniorStatus, { tone: StatusTone; icon: IconName; text: string }> = {
  protected: { tone: 'green', icon: 'shield', text: 'Anioł Stróż słucha. Nic nie jest nagrywane.' },
  basic: { tone: 'yellow', icon: 'warning', text: 'Podstawowa ochrona (bez AI)' },
  paused: { tone: 'yellow', icon: 'pause', text: 'Ochrona wstrzymana' },
  not_hearing: { tone: 'red', icon: 'mic-off', text: 'Nie słyszę rozmowy' },
  offline: { tone: 'red', icon: 'cloud-off', text: 'Anioł Stróż jest offline' },
};
