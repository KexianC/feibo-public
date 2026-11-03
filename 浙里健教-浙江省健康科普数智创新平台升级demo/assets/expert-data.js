(function () {
  'use strict';

  var library = {
    source: {
      title: '省卫生健康委办公室等关于公布浙江省健康科普专家库分库成员名单的通知',
      documentNo: '浙卫办〔2023〕13号',
      issuedAt: '2023-08-17',
      publishedAt: '2023-09-15 08:42',
      url: 'https://wsjkw.zj.gov.cn/art/2023/9/15/art_1229560650_2491204.html',
      sortNote: '以姓氏笔画为序排列'
    },
    totalPeople: 2351,
    totalLibraries: 24,
    libraries: []
  };

  window.ZJ_EXPERT_LIBRARY = library;
  window.addZJExpertLibraries = function (rows) {
    rows.forEach(function (row) {
      var people = [];
      var seen = Object.create(null);
      var addPerson = function (name, roles) {
        if (!name) return;
        if (seen[name] != null) {
          roles.forEach(function (role) {
            if (people[seen[name]].roles.indexOf(role) === -1) people[seen[name]].roles.push(role);
          });
          return;
        }
        seen[name] = people.length;
        people.push({ id: row[0] + '-P' + String(people.length + 1).padStart(3, '0'), name: name, roles: roles });
      };

      row[4].forEach(function (entry) { addPerson(entry[0], entry[1].split('/')); });
      if (row[5]) row[5].split('|').forEach(function (name) { addPerson(name, ['成员']); });
      library.libraries.push({ id: row[0], name: row[1], leadUnit: row[2], count: row[3], people: people });
    });
  };
})();
