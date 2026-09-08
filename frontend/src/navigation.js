import { createContext, useContext } from 'react';

export const WorkspaceNavigationContext = createContext({ navigate: () => {} });

export function useWorkspaceNavigation() {
  return useContext(WorkspaceNavigationContext);
}
