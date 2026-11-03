import assert from 'node:assert/strict';
import {
  mapRoleIdentity,
  requireDatabaseRoleId,
} from '../src/services/roleIdentity.ts';

const mapped = mapRoleIdentity({
  id: '90071992547409931',
  roleId: '80071992547409932',
});
assert.equal(mapped.databaseId, '90071992547409931');
assert.equal(mapped.roleId, '80071992547409932');
assert.equal(mapped.id, '80071992547409932');

assert.deepEqual(mapRoleIdentity({ id: 7 }), {
  id: '7',
  databaseId: '7',
  roleId: undefined,
});

const missingDatabaseId = mapRoleIdentity({ roleId: '9' });
assert.equal(missingDatabaseId.id, '9');
assert.equal(missingDatabaseId.databaseId, undefined);
assert.throws(() => requireDatabaseRoleId(missingDatabaseId.databaseId));

assert.equal(requireDatabaseRoleId(' 123 '), '123');
for (const invalidId of ['', undefined, 'role-1', '1/2']) {
  assert.throws(() => requireDatabaseRoleId(invalidId));
}

console.log('role identity contract passed');
