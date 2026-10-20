export const environment = {
  // Sem backend ainda: em desenvolvimento a API e o login rodam em memória.
  // Para usar o backend e o Keycloak reais, troque para false.
  useMocks: true,
  apiUrl: '/api/v1',
  oidc: {
    authority: 'http://localhost:8081/realms/pmdf',
    clientId: 'svg-web',
  },
};
