import { StageId } from '../api/model/models';
import { StageLabelPipe } from './stage-label.pipe';

describe('StageLabelPipe', () => {
  const pipe = new StageLabelPipe();

  it('names every stage in Polish', () => {
    for (const stage of Object.values(StageId)) {
      expect(pipe.transform(stage)).not.toBe(stage);
    }
    expect(pipe.transform(StageId.SECRECY_DEMAND)).toBe('Prośba o tajemnicę');
  });
});
