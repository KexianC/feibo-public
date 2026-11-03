export class ComposeRunGuard {
  private currentRunId = 0;

  begin(): number {
    this.currentRunId += 1;
    return this.currentRunId;
  }

  isCurrent(runId: number): boolean {
    return runId === this.currentRunId;
  }

  runIfCurrent(runId: number, update: () => void): boolean {
    if (!this.isCurrent(runId)) {
      return false;
    }

    update();
    return true;
  }
}
