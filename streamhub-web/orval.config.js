/** @type {import('orval').Config} */
module.exports = {
  streamhub: {
    input: {
      target: "http://localhost:8080/v3/api-docs",
    },
    output: {
      mode: "tags-split",
      target: "src/apis/query",
      client: "react-query",
      prettier: true,
      override: {
        mutator: {
          path: "src/apis/custom-instance.ts",
          name: "customInstance",
        },
        query: {
          useQuery: true,
          useMutation: true,
        },
      },
    },
  },
};
